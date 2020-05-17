/*
 * Copyright (C) 2014 Advanced Card Systems Ltd. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Advanced
 * Card Systems Ltd. ("Confidential Information").  You shall not disclose such
 * Confidential Information and shall use it only in accordance with the terms
 * of the license agreement you entered into with ACS.
 */

package com.nuvopoint.flutter_nfc_acs;

import java.util.Locale;

class Utils {
  static String toHexString(byte[] array) {

    StringBuilder bufferString = new StringBuilder();

    if (array != null) {
      for (byte b : array) {
        String hexChar = Integer.toHexString(b & 0xFF);
        if (hexChar.length() == 1) {
          hexChar = "0" + hexChar;
        }
        bufferString.append(hexChar.toUpperCase(Locale.US)).append(" ");
      }
    }

    return bufferString.toString();
  }

  static byte[] toByteArray(String hexString) {
    int hexStringLength = hexString.length();
    byte[] byteArray;
    int count = 0;
    char c;
    int i;

    // Count number of hex characters
    for (i = 0; i < hexStringLength; i++) {

      c = hexString.charAt(i);
      if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
          && c <= 'f') {
        count++;
      }
    }

    byteArray = new byte[(count + 1) / 2];
    boolean first = true;
    int len = 0;
    int value;
    for (i = 0; i < hexStringLength; i++) {

      c = hexString.charAt(i);
      if (c >= '0' && c <= '9') {
        value = c - '0';
      } else if (c >= 'A' && c <= 'F') {
        value = c - 'A' + 10;
      } else if (c >= 'a' && c <= 'f') {
        value = c - 'a' + 10;
      } else {
        value = -1;
      }

      if (value >= 0) {
        if (first) {
          byteArray[len] = (byte) (value << 4);
        } else {
          byteArray[len] |= value;
          len++;
        }
        first = !first;
      }
    }

    return byteArray;
  }

  static byte[] hexStringToByteArray(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
          + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }

//  static byte[] hexString2Bytes(String string) {
//    if (string == null)
//      throw new NullPointerException("string was null");
//
//    int len = string.length();
//
//    if (len == 0)
//      return new byte[0];
//    if (len % 2 == 1)
//      throw new IllegalArgumentException(
//          "string length should be an even number");
//
//    byte[] ret = new byte[len / 2];
//    byte[] tmp = string.getBytes();
//
//    for (int i = 0; i < len; i += 2) {
//      if (!isHexNumber(tmp[i]) || !isHexNumber(tmp[i + 1])) {
//        throw new NumberFormatException(
//            "string contained invalid value");
//      }
//      ret[i / 2] = uniteBytes(tmp[i], tmp[i + 1]);
//    }
//    return ret;
//  }

//  public static byte[] getHexBytes2(String rawdata) {
//    if (rawdata == null || rawdata.isEmpty()) {
//      return null;
//    }
//
//    String command = rawdata.replace(" ", "").replace("\n", "");
//
//    if (command.isEmpty() || command.length() % 2 != 0
//        || isHexNumber(command) == false) {
//      return null;
//    }
//
//    return hexString2Bytes(command);
//  }
}
