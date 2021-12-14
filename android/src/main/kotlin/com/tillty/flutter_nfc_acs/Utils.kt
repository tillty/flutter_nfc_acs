/*
 * Copyright (C) 2014 Advanced Card Systems Ltd. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Advanced
 * Card Systems Ltd. ("Confidential Information").  You shall not disclose such
 * Confidential Information and shall use it only in accordance with the terms
 * of the license agreement you entered into with ACS.
 */
package com.tillty.flutter_nfc_acs

import java.util.*

internal object Utils {
    fun toHexString(array: ByteArray?): String {
        val bufferString = StringBuilder()
        if (array != null) {
            for (b in array) {
                var hexChar = Integer.toHexString(b.toInt() and 0xFF)
                if (hexChar.length == 1) {
                    hexChar = "0$hexChar"
                }
                bufferString.append(hexChar.uppercase(Locale.US)).append(" ")
            }
        }
        return bufferString.toString()
    }

    /*fun toByteArray(hexString: String): ByteArray {
        val hexStringLength = hexString.length
        val byteArray: ByteArray
        var count = 0
        var c: Char

        // Count number of hex characters
        var i = 0
        while (i < hexStringLength) {
            c = hexString[i]
            if (c in '0'..'9' || c in 'A'..'F' || (c in 'a'..'f')
            ) {
                count++
            }
            i++
        }
        byteArray = ByteArray((count + 1) / 2)
        var first = true
        var len = 0
        var value: Int
        i = 0
        while (i < hexStringLength) {
            c = hexString[i]
            value = when (c) {
                in '0'..'9' -> {
                    c - '0'
                }
                in 'A'..'F' -> {
                    c - 'A' + 10
                }
                in 'a'..'f' -> {
                    c - 'a' + 10
                }
                else -> {
                    -1
                }
            }
            if (value >= 0) {
                if (first) {
                    byteArray[len] = (value shl 4).toByte()
                } else {
                    byteArray[len] = byteArray[len] or value.toByte()
                    len++
                }
                first = !first
            }
            i++
        }
        return byteArray
    }*/

    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    } //  static byte[] hexString2Bytes(String string) {
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