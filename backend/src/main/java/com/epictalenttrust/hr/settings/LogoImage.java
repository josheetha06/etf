package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.common.ApiException;

/** Checks an uploaded logo by its first bytes, not by the name or type the browser sends. */
public final class LogoImage {

    public static final int MAX_BYTES = 1024 * 1024;

    private LogoImage() {
    }

    /** Returns the image type, or throws a plain error HR can act on. */
    public static String contentType(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("logo_empty", "The file is empty. Choose a PNG, JPG or WebP image.");
        }
        if (bytes.length > MAX_BYTES) {
            throw ApiException.badRequest("logo_too_big", "The logo is larger than 1 MB. Please use a smaller image.");
        }
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw ApiException.badRequest("logo_type",
                "This file isn't a PNG, JPG or WebP image. SVG and other formats aren't accepted.");
    }
}
