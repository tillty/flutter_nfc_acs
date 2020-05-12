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

import android.text.Editable;
import android.widget.EditText;

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

//  private static boolean isHexNumber(byte value) {
//    if (!(value >= '0' && value <= '9') && !(value >= 'A' && value <= 'F')
//        && !(value >= 'a' && value <= 'f')) {
//      return false;
//    }
//    return true;
//  }

//  public static boolean isHexNumber(String string) {
//    if (string == null)
//      throw new NullPointerException("string was null");
//
//    boolean flag = true;
//
//    for (int i = 0; i < string.length(); i++) {
//      char cc = string.charAt(i);
//      if (!isHexNumber((byte) cc)) {
//        flag = false;
//        break;
//      }
//    }
//    return flag;
//  }

//  private static byte uniteBytes(byte src0, byte src1) {
//    byte _b0 = Byte.decode("0x" + new String(new byte[] { src0 }))
//        .byteValue();
//    _b0 = (byte) (_b0 << 4);
//    byte _b1 = Byte.decode("0x" + new String(new byte[] { src1 }))
//        .byteValue();
//    byte ret = (byte) (_b0 ^ _b1);
//    return ret;
//  }

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
