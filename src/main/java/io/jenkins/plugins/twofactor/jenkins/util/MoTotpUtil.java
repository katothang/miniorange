/*
 * Copyright (c) 2023
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.jenkins.plugins.twofactor.jenkins.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Utility class for TOTP (Time-based One-Time Password) operations
 * Uses Google Authenticator library for TOTP generation and validation
 * Uses ZXing library for QR code generation
 */
public class MoTotpUtil {
    private static final Logger LOGGER = Logger.getLogger(MoTotpUtil.class.getName());
    private static final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private static final int QR_CODE_SIZE = 300;

    /**
     * Generate a new secret key for TOTP
     * @return Secret key as a string
     */
    public static String generateSecretKey() {
        try {
            GoogleAuthenticatorKey key = gAuth.createCredentials();
            return key.getKey();
        } catch (Exception e) {
            LOGGER.severe("Error generating secret key: " + e.getMessage());
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }

    /**
     * Validate a TOTP code against a secret key
     * @param secretKey The secret key
     * @param code The TOTP code to validate
     * @return true if the code is valid, false otherwise
     */
    public static boolean validateTotpCode(String secretKey, int code) {
        try {
            return gAuth.authorize(secretKey, code);
        } catch (Exception e) {
            LOGGER.warning("Error validating TOTP code: " + e.getMessage());
            return false;
        }
    }

    /**
     * Generate QR code URL for Google Authenticator
     * @param secretKey The secret key
     * @param accountName The account name (usually username)
     * @param issuer The issuer name (usually "Jenkins")
     * @return QR code URL
     */
    public static String generateQRCodeUrl(String secretKey, String accountName, String issuer) {
    // tự build URL otpauth://
    String otpAuthUrl = String.format(
        "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
        issuer, accountName, secretKey, issuer
    );
    return otpAuthUrl;
}

    /**
     * Generate QR code image as Base64 encoded string
     * @param secretKey The secret key
     * @param accountName The account name (usually username)
     * @param issuer The issuer name (usually "Jenkins")
     * @return Base64 encoded QR code image
     */
    public static String generateQRCodeImageBase64(String secretKey, String accountName, String issuer) {
        try {
            String qrCodeUrl = generateQRCodeUrl(secretKey, accountName, issuer);
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeUrl, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] qrCodeBytes = outputStream.toByteArray();
            
            return Base64.getEncoder().encodeToString(qrCodeBytes);
        } catch (WriterException | IOException e) {
            LOGGER.severe("Error generating QR code: " + e.getMessage());
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Generate QR code image as data URI for HTML img tag
     * @param secretKey The secret key
     * @param accountName The account name (usually username)
     * @param issuer The issuer name (usually "Jenkins")
     * @return Data URI string (data:image/png;base64,...)
     */
    public static String generateQRCodeDataUri(String secretKey, String accountName, String issuer) {
        String base64Image = generateQRCodeImageBase64(secretKey, accountName, issuer);
        return "data:image/png;base64," + base64Image;
    }
}

