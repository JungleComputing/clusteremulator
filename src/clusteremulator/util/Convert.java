package clusteremulator.util;

import java.text.*;

public class Convert {

    private static final DecimalFormat formatter = new DecimalFormat();

    public static double toSec(double value, String unit)
    throws IllegalArgumentException
    {
        unit = unit.trim();

        if (unit.equals("s") || unit.equals("sec") || unit.equals("seconds")) {
            return value;
        } else if (unit.equals("ms") || unit.equals("msec")
                || unit.equals("millisec") || unit.equals("milliseconds")) {
            return millisecToSec(value);
        } else {
            throw new IllegalArgumentException("cannot convert " + unit
                    + " to seconds");
        }
    }

    public static double toBytes(double value, String unit)
    throws IllegalArgumentException
    {
        unit = unit.trim();

        if (unit.equalsIgnoreCase("B") || 
            unit.equalsIgnoreCase("byte") ||
            unit.equalsIgnoreCase("bytes")) {
            return value;
        } else if (unit.equalsIgnoreCase("KB") || 
                unit.equalsIgnoreCase("KiB") ||
                unit.equalsIgnoreCase("kbyte") ||
                unit.equalsIgnoreCase("kbytes") || 
                unit.equalsIgnoreCase("kilobyte") ||
                unit.equalsIgnoreCase("kilobytes")) {
            return kbytesToBytes(value);
        } else if (unit.equalsIgnoreCase("MB") || 
                unit.equalsIgnoreCase("MiB") ||
                unit.equalsIgnoreCase("mbyte") || 
                unit.equalsIgnoreCase("mbytes") ||
                unit.equalsIgnoreCase("megabytes") ||
                unit.equalsIgnoreCase("megabytes")) {
            return mbytesToBytes(value);
        } else if (unit.equalsIgnoreCase("kbit") ||
                unit.equalsIgnoreCase("kbits") ||
                unit.equalsIgnoreCase("kilobits")) {
            return kbitsToBytes(value);
        } else if (unit.equalsIgnoreCase("mbit") ||
                unit.equalsIgnoreCase("mbits") ||
                unit.equalsIgnoreCase("megabits")) {
            return mbitsToBytes(value);
        } else if (unit.equalsIgnoreCase("GB") || 
                unit.equalsIgnoreCase("GiB") ||
                unit.equalsIgnoreCase("gbyte") ||
                unit.equalsIgnoreCase("gbytes") ||
                unit.equalsIgnoreCase("gigabyte") ||
                unit.equalsIgnoreCase("gigabytes")) {
            return gbytesToBytes(value);
        } else if (unit.equalsIgnoreCase("gbit")
                || unit.equalsIgnoreCase("gbits")
                || unit.equalsIgnoreCase("gigabit")
                || unit.equalsIgnoreCase("gigabits")) {
            return gbitsToBytes(value);
        } else {
            throw new IllegalArgumentException("cannot convert " + unit
                    + " to bytes");
        }
    }

    public static double toBytesPerSec(double value, String unit)
    throws IllegalArgumentException
    {
        unit = unit.trim();

        int slashIndex = unit.indexOf('/');

        if (slashIndex < 0) {
            throw new IllegalArgumentException("missing '/' in unit " + unit);
        } else if (slashIndex == 0) {
            throw new IllegalArgumentException("missing data unit in " + unit);
        } else if (slashIndex == unit.length() - 1) {
            throw new IllegalArgumentException("missing time unit in " + unit);
        }

        String dataUnit = unit.substring(0, slashIndex);
        String timeUnit = unit.substring(slashIndex + 1);

        double result = toBytes(value, dataUnit);
        result /= toSec(1.0, timeUnit);

        return result;
    }

    public static double parseNumber(String number) {
        int i = 0;
        while (i < number.length()
                && (Character.isDigit(number.charAt(i)) || 
                        number.charAt(i) == '.')) {
            i++;
        }
        return Double.parseDouble(number.substring(0, i));
    }

    public static String parseUnit(String value) {
        int i = 0;
        while (i < value.length()
                && (Character.isDigit(value.charAt(i)) || 
                        value.charAt(i) == '.')) {
            i++;
        }
        return value.substring(i);
    }

    public static double parseBytes(String s) {
        double number = parseNumber(s);
        String unit = parseUnit(s);
        return toBytes(number, unit);
    }

    public static double parseSec(String s) {
        double number = parseNumber(s);
        String unit = parseUnit(s);
        return toSec(number, unit);
    }

    public static double parseBytesPerSec(String s) {
        double number = parseNumber(s);
        String unit = parseUnit(s);
        return toBytesPerSec(number, unit);
    }

    public static long mbitsToBytes(double mbits) {
        return (long) (mbits * 1000 * 1000 / 8);
    }

    public static double mbitsToMBytes(double mbits) {
        // mbits * 8 * 1000 * 1000 / 1024.0 / 1024.0;
        return mbits * 1000000 / 131072.0;
    }

    public static double mbytesToMbits(double mbytes) {
        return mbytes * 131072 / 1000000;
    }

    public static double mbytesToBytes(double mbytes) {
        return mbytes * 1024 * 1024; 
    }

    public static double gbytesToBytes(double gbytes) {
        return gbytes * 1024 * 1024 * 1024;
    }

    public static double gbitsToBytes(double gbits) {
        return gbits * 1000 * 1000 * 1000 / 8;
    }

    public static long mbitsPerSecToBytesPerMillisec(double mbitsPerSec) {
        return (long) (mbitsToBytes(mbitsPerSec) / 1000.0);
    }

    public static double mbytesPerSecToBytesPerSec(double mbytesPerSec) {
        return mbytesPerSec * 1024.0 * 1024.0;
    }

    public static long mbytesPerSecToBytesPerMillisec(double mbytesPerSec) {
        return (long) (mbytesToBytes(mbytesPerSec) / 1000.0);
    }

    public static long mbytesPerSecToBytesPerNanosec(double mbytesPerSec) {
        return (long) (mbytesToBytes(mbytesPerSec) / 1000000000.0);
    }

    public static double bytesPerSecToKBytesPerSec(double bytesPerSec) {
        return bytesPerSec / 1024.0;
    }

    public static double bytesPerSecToMBytesPerSec(double bytesPerSec) {
        return bytesPerSec / 1024.0 / 1024.0;
    }

    public static double bytesPerSecToBytesPerNanosec(double bytesPerSec) {
        return bytesPerSec / 1000.0 / 1000.0 / 1000.0;
    }

    public static double bytesPerSecToBytesPerMillisec(double bytesPerSec) {
        return bytesPerSec / 1000.0;
    }

    public static double bytesPerMillisecToBytesPerSec(double bytesPerMillisec)
    {
        return bytesPerMillisec * 1000.0;
    }

    public static double bytesPerNanosecToBytesPerSec(double bytesPerNanosec) {
        return bytesPerNanosec * 1000000000.0;
    }

    public static double bytesPerNanosecToKBytesPerSec(double bytesPerNanosec) {
        return bytesPerNanosec * 1000000000.0 / 1024.0;
    }

    public static double bytesPerNanosecToMBytesPerSec(double bytesPerNanosec) {
        return bytesPerNanosec * 1000000000.0 / 1024.0 / 1024.0;
    }

    public static double bytesToMbits(int bytes) {
        // bytes / 1000.0 / 1000.0 * 8;
        return bytes / 125000;
    }

    public static double bytesToMbits(long bytes) {
        // bytes / 1000.0 / 1000.0 * 8;
        return bytes / 125000;
    }

    public static double bytesToMBytes(int bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    public static double bytesToMBytes(long bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    public static double bytesToMBytes(double bytes) {
        return bytes / 1024.0 / 1024.0;
    }

    public static double bytesToKBytes(long bytes) {
        return bytes / 1024.0;
    }

    public static int kbytesToBytes(double kbytes) {
        return (int) (Math.round(kbytes * 1024));
    }

    public static double kbytesToMBytes(double kbytes) {
        return kbytes / 1024.0;
    }

    public static double kbitsToBytes(double kbits) {
        return kbits / 8000.0;
    }

    public static double millisecToSec(long msec) {
        return msec / 1000.0;
    }

    public static double millisecToSec(double msec) {
        return msec / 1000.0;
    }

    public static long millisecToNanosec(double msec) {
        return (long) (msec * 1000 * 1000);
    }

    public static long secToMillisec(double sec) {
        return (long) (sec * 1000);
    }

    public static long secToNanosec(double sec) {
        return (long) (sec * 1000000000L);
    }

    public static double microsecToSec(double microsec) {
        return microsec / 1000000L;
    }

    public static double microsecToMillisec(double microsec) {
        return microsec / 1000;
    }

    public static double nanosecToSec(double nanosec) {
        return nanosec / 1000000000L;
    }

    public static double nanosecToMillisec(double nanosec) {
        return Math.floor(nanosec / 1000000L);
    }

    public static String round(double value, int noDecimals) {
        formatter.setMinimumFractionDigits(noDecimals);
        formatter.setMaximumFractionDigits(noDecimals);
        formatter.setGroupingSize(0);
        return formatter.format(value);
    }

    public static double ceilVal(double value, int noDecimals) {
        double pow = Math.pow(10, noDecimals);
        return Math.ceil(value * pow) / pow;
    }

    public static String prepadZeros(int i) {
        if (i < 10) {
            return "0" + i;
        } else {
            return Integer.toString(i);
        }

    }

    public static String prepad(String s, char c, int totalLength) {
        if (s.length() >= totalLength) {
            return s;
        } else {
            return chars(c, totalLength - s.length()) + s;
        }
    }

    private static String chars(char c, int number) {
        String result = "";
        for (int i = 0; i < number; i++) {
            result += c;
        }
        return result;
    }

    public static byte booleanToByte(boolean b) {
        return b ? (byte) 1 : (byte) 0;
    }

    public static boolean byteToBoolean(byte b) {
        return (b == 0) ? false : true;
    }

    public static String toString(int[] a) {
        if (a == null) {
            return "null";
        } else {
            String result = "[";
            String concat = "";
            for (int i = 0; i < a.length; i++) {
                result += Integer.toString(a[i]) + concat;
                concat = ", ";
            }
            result += "]";
            return result;
        }
    }

    public static String toString(String[] a) {
        if (a == null) {
            return "null";
        } else {
            String result = "[";
            String concat = "";
            for (int i = 0; i < a.length; i++) {
                result += a[i] + concat;
                concat = ", ";
            }
            result += "]";
            return result;
        }
    }

}
