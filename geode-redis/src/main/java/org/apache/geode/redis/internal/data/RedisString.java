/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.apache.geode.redis.internal.data;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.Region;
import org.apache.geode.redis.internal.RedisConstants;
import org.apache.geode.redis.internal.delta.AppendDeltaInfo;
import org.apache.geode.redis.internal.delta.DeltaInfo;
import org.apache.geode.redis.internal.netty.Coder;

public class RedisString extends AbstractRedisData {
  private ByteArrayWrapper value;

  public RedisString(ByteArrayWrapper value) {
    this.value = value;
  }

  // for serialization
  public RedisString() {}

  public int append(ByteArrayWrapper appendValue,
      Region<ByteArrayWrapper, RedisData> region,
      ByteArrayWrapper key) {
    value.append(appendValue.toBytes());
    storeChanges(region, key, new AppendDeltaInfo(appendValue.toBytes()));
    return value.length();
  }

  public ByteArrayWrapper get() {
    return new ByteArrayWrapper(value.toBytes());
  }

  public void set(ByteArrayWrapper value) {
    this.value = value;
  }

  public long incr(Region<ByteArrayWrapper, RedisData> region, ByteArrayWrapper key)
      throws NumberFormatException, ArithmeticException {
    long longValue = parseValueAsLong();
    if (longValue == Long.MAX_VALUE) {
      throw new ArithmeticException(RedisConstants.ERROR_OVERFLOW);
    }
    longValue++;
    String stringValue = Long.toString(longValue);
    value.setBytes(Coder.stringToBytes(stringValue));
    // numeric strings are short so no need to use delta
    region.put(key, this);
    return longValue;
  }

  public long incrby(Region<ByteArrayWrapper, RedisData> region, ByteArrayWrapper key,
      long increment)
      throws NumberFormatException, ArithmeticException {
    long longValue = parseValueAsLong();
    if (longValue >= 0 && increment > (Long.MAX_VALUE - longValue)) {
      throw new ArithmeticException(RedisConstants.ERROR_OVERFLOW);
    }
    longValue += increment;
    String stringValue = Long.toString(longValue);
    value.setBytes(Coder.stringToBytes(stringValue));
    // numeric strings are short so no need to use delta
    region.put(key, this);
    return longValue;
  }

  public long decrby(Region<ByteArrayWrapper, RedisData> region, ByteArrayWrapper key,
      long decrement) {
    long longValue = parseValueAsLong();
    if (longValue <= 0 && -decrement < (Long.MIN_VALUE - longValue)) {
      throw new ArithmeticException(RedisConstants.ERROR_OVERFLOW);
    }
    longValue -= decrement;
    String stringValue = Long.toString(longValue);
    value.setBytes(Coder.stringToBytes(stringValue));
    // numeric strings are short so no need to use delta
    region.put(key, this);
    return longValue;
  }

  public long decr(Region<ByteArrayWrapper, RedisData> region, ByteArrayWrapper key)
      throws NumberFormatException, ArithmeticException {
    long longValue = parseValueAsLong();
    if (longValue == Long.MIN_VALUE) {
      throw new ArithmeticException(RedisConstants.ERROR_OVERFLOW);
    }
    longValue--;
    String stringValue = Long.toString(longValue);
    value.setBytes(Coder.stringToBytes(stringValue));
    // numeric strings are short so no need to use delta
    region.put(key, this);
    return longValue;
  }

  private long parseValueAsLong() {
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException ex) {
      throw new NumberFormatException(RedisConstants.ERROR_NOT_INTEGER);
    }
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    super.toData(out);
    DataSerializer.writeByteArray(value.toBytes(), out);
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    super.fromData(in);
    value = new ByteArrayWrapper(DataSerializer.readByteArray(in));
  }

  @Override
  protected void applyDelta(DeltaInfo deltaInfo) {
    AppendDeltaInfo appendDeltaInfo = (AppendDeltaInfo) deltaInfo;
    byte[] appendBytes = appendDeltaInfo.getBytes();
    if (value == null) {
      value = new ByteArrayWrapper(appendBytes);
    } else {
      value.append(appendBytes);
    }
  }

  @Override
  public RedisDataType getType() {
    return RedisDataType.REDIS_STRING;
  }

  @Override
  protected boolean removeFromRegion() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RedisString)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    RedisString that = (RedisString) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value);
  }
}
