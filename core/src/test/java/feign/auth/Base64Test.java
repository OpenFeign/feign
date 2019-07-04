/**
 * Copyright 2012-2019 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.auth;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Base64Test {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  /* testedClasses: Base64 */

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput0PositiveOutput0() {

    // Arrange
    final byte[] in = {};
    final int len = 2;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertArrayEquals(new byte[] {}, retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput3Output0() {

    // Arrange
    final byte[] in = {(byte) -115, (byte) 80, (byte) 10};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertArrayEquals(new byte[] {}, retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull() {

    // Arrange
    final byte[] in = {(byte) -115, (byte) 80, (byte) 74, (byte) 10};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull2() {

    // Arrange
    final byte[] in = {(byte) -115, (byte) 80, (byte) 74, (byte) 9};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull3() {

    // Arrange
    final byte[] in = {(byte) 59, (byte) 80, (byte) 74, (byte) 9};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull4() {

    // Arrange
    final byte[] in = {(byte) 10, (byte) 64, (byte) 74, (byte) 9};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull5() {

    // Arrange
    final byte[] in = {(byte) 10, (byte) 64, (byte) 106, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull6() {

    // Arrange
    final byte[] in = {(byte) 10, (byte) 96, (byte) 106, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull7() {

    // Arrange
    final byte[] in = {(byte) 9, (byte) 96, (byte) 106, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput4OutputNull8() {

    // Arrange
    final byte[] in = {(byte) 13, (byte) 124, (byte) 106, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput7PositiveOutputNull() {

    // Arrange
    final byte[] in =
        {(byte) 47, (byte) 127, (byte) 126, (byte) 61, (byte) 91, (byte) 126, (byte) 61};
    final int len = 4;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput7PositiveOutputNull2() {

    // Arrange
    final byte[] in =
        {(byte) 43, (byte) 127, (byte) 126, (byte) 61, (byte) 91, (byte) 126, (byte) 61};
    final int len = 4;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput7PositiveOutputNull3() {

    // Arrange
    final byte[] in =
        {(byte) 97, (byte) 127, (byte) 124, (byte) 61, (byte) 91, (byte) 126, (byte) 63};
    final int len = 4;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput7PositiveOutputNull4() {

    // Arrange
    final byte[] in =
        {(byte) 52, (byte) 127, (byte) 124, (byte) 61, (byte) 91, (byte) 126, (byte) 63};
    final int len = 4;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput7PositiveOutputNull5() {

    // Arrange
    final byte[] in =
        {(byte) 81, (byte) 127, (byte) 124, (byte) 61, (byte) 91, (byte) 126, (byte) 63};
    final int len = 4;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput9OutputNull() {

    // Arrange
    final byte[] in = {(byte) 47, (byte) 127, (byte) -8, (byte) 47, (byte) -83,
        (byte) 112, (byte) -23, (byte) -8, (byte) 47};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10OutputNull() {

    // Arrange
    final byte[] in = {(byte) 50, (byte) 124, (byte) -119, (byte) 61, (byte) -35,
        (byte) 80, (byte) -71, (byte) -20, (byte) -119, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10OutputNull2() {

    // Arrange
    final byte[] in = {(byte) 98, (byte) 124, (byte) -103, (byte) 61, (byte) -51,
        (byte) 80, (byte) -71, (byte) -20, (byte) -103, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10OutputNull3() {

    // Arrange
    final byte[] in = {(byte) 74, (byte) 124, (byte) -103, (byte) 61, (byte) -51,
        (byte) 80, (byte) -71, (byte) -20, (byte) -103, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10OutputNull4() {

    // Arrange
    final byte[] in = {(byte) 43, (byte) 124, (byte) -8, (byte) 61, (byte) -83,
        (byte) 112, (byte) -7, (byte) -19, (byte) -8, (byte) 61};

    // Act
    final byte[] retval = Base64.decode(in);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull() {

    // Arrange
    final byte[] in = {(byte) 29, (byte) 66, (byte) -100, (byte) -100, (byte) 28,
        (byte) 29, (byte) 66, (byte) 28, (byte) 28, (byte) -116};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull2() {

    // Arrange
    final byte[] in = {(byte) 58, (byte) 10, (byte) -100, (byte) -104, (byte) 28,
        (byte) 25, (byte) 74, (byte) 16, (byte) 28, (byte) -120};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull3() {

    // Arrange
    final byte[] in = {(byte) 58, (byte) 10, (byte) -50, (byte) -118, (byte) 94,
        (byte) 75, (byte) 32, (byte) 66, (byte) 78, (byte) -38};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull4() {

    // Arrange
    final byte[] in = {(byte) 58, (byte) 10, (byte) -50, (byte) -118, (byte) 94,
        (byte) 107, (byte) 9, (byte) 98, (byte) 110, (byte) -6};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull5() {

    // Arrange
    final byte[] in = {(byte) 13, (byte) 58, (byte) -50, (byte) -118, (byte) 94,
        (byte) 107, (byte) 9, (byte) 98, (byte) 110, (byte) -6};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull6() {

    // Arrange
    final byte[] in = {(byte) 9, (byte) 58, (byte) -50, (byte) -118, (byte) 94,
        (byte) 107, (byte) 9, (byte) 98, (byte) 110, (byte) -6};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull7() {

    // Arrange
    final byte[] in = {(byte) 47, (byte) 58, (byte) -50, (byte) -118, (byte) 94,
        (byte) 107, (byte) 9, (byte) 98, (byte) 110, (byte) -6};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void decodeInput10PositiveOutputNull8() {

    // Arrange
    final byte[] in = {(byte) 47, (byte) 58, (byte) -50, (byte) -38, (byte) 90,
        (byte) 63, (byte) 61, (byte) 118, (byte) 62, (byte) -6};
    final int len = 7;

    // Act
    final byte[] retval = Base64.decode(in, len);

    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void encodeInput0OutputNotNull() {

    // Arrange
    final byte[] in = {};

    // Act
    final String retval = Base64.encode(in);

    // Assert result
    Assert.assertEquals("", retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void encodeInputNullOutputNullPointerException() {

    // Arrange
    final byte[] in = null;

    // Act
    thrown.expect(NullPointerException.class);
    Base64.encode(in);

    // Method is not expected to return due to exception thrown
  }
}
