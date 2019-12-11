/*
 * Copyright 2014 Parity authors Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.kmfrog.martlet.util;

import static com.kmfrog.martlet.C.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.StringUtils;

/**
 * This class contains utility methods for working with timestamps.
 */
public class Fmt {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Fmt() {
    }

    /**
     * Format a timestamp as a string.
     *
     * @param timestampMillis a timestamp in milliseconds
     * @return the timestamp as a string
     */
    public static String formatHMSsss(long timestampMillis) {
        return FORMATTER.format(LocalTime.ofNanoOfDay(timestampMillis % (24 * 60 * 60 * 1000) * 1_000_000));
    }

    public static String fmtDec(BigDecimal dec, int fractionDigits) {
        DecimalFormat decFmt = new DecimalFormat(StringUtils.rightPad("0.", 2 + fractionDigits, "0"));
        return decFmt.format(dec.divide(BigDecimal.valueOf(POWERS_OF_TEN[fractionDigits])));
    }

    public static BigDecimal dec(long val, int scale) {
        return BigDecimal.valueOf(val, scale);
    }

    public static String fmtNum(long num, int factorDigits) {
        return fmtDec(BigDecimal.valueOf(num), factorDigits);
    }

    public static void main(String[] args) {
        // 744768000000,444037000,0;444037000
        System.out.println(fmtDec(BigDecimal.valueOf(4037000L), 8));
    }

}
