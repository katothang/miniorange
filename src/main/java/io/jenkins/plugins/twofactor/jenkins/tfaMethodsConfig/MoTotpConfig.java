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
package io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig;

import static io.jenkins.plugins.twofactor.constants.MoGlobalConfigConstant.AdminConfiguration.ENABLE_2FA_FOR_ALL_USERS;
import static io.jenkins.plugins.twofactor.constants.MoGlobalConfigConstant.UtilityGlobalConstants.SESSION_2FA_VERIFICATION;
import static io.jenkins.plugins.twofactor.constants.MoPluginUrls.Urls.MO_TOTP_CONFIG;
import static io.jenkins.plugins.twofactor.jenkins.MoFilter.moPluginSettings;
import static io.jenkins.plugins.twofactor.jenkins.MoFilter.userAuthenticationStatus;
import static jenkins.model.Jenkins.get;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.*;
import hudson.util.FormApply;
import hudson.util.Secret;
import io.jenkins.plugins.twofactor.jenkins.MoGlobalConfig;
import io.jenkins.plugins.twofactor.jenkins.MoUserAuth;
import io.jenkins.plugins.twofactor.jenkins.util.MoTotpUtil;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.lang.NonNull;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Logger;

public class MoTotpConfig extends UserProperty implements Action {
    private static final Logger LOGGER = Logger.getLogger(MoTotpConfig.class.getName());
    private Secret secretKey;
    private boolean isConfigured;

    @DataBoundConstructor
    public MoTotpConfig(Secret secretKey, boolean isConfigured) {
        this.secretKey = secretKey;
        this.isConfigured = isConfigured;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "TOTP Authenticator";
    }

    @Override
    public String getUrlName() {
        return MO_TOTP_CONFIG.getUrl();
    }

    @SuppressWarnings("unused")
    public boolean isUserAuthenticatedFromTfa() {
        return userAuthenticationStatus.getOrDefault(user.getId(), false);
    }

    @SuppressWarnings("unused")
    public String getUserId() {
        return user != null ? user.getId() : "";
    }

    @SuppressWarnings("unused")
    public String getBaseUrl() {
        return get().getRootUrl();
    }

    public static String getContextPath() {
        return MoUserAuth.getContextPath();
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    public void setConfigured(boolean configured) {
        isConfigured = configured;
    }

    public Secret getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(Secret secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Generate QR code for TOTP setup
     * @return QR code as data URI
     */
    @SuppressWarnings("unused")
    public String getQRCodeDataUri() {
        try {
            if (secretKey == null || secretKey.getPlainText().isEmpty()) {
                // Generate new secret key if not exists
                String newSecretKey = MoTotpUtil.generateSecretKey();
                this.secretKey = Secret.fromString(newSecretKey);
                user.save();
            }
            
            String accountName = user.getId();
            String issuer = "Jenkins";
            return MoTotpUtil.generateQRCodeDataUri(secretKey.getPlainText(), accountName, issuer);
        } catch (Exception e) {
            LOGGER.severe("Error generating QR code: " + e.getMessage());
            return "";
        }
    }

    /**
     * Get the secret key in plain text for display
     * @return Secret key string
     */
    @SuppressWarnings("unused")
    public String getSecretKeyPlainText() {
        if (secretKey == null) {
            return "";
        }
        return secretKey.getPlainText();
    }

    /**
     * Save TOTP configuration
     */
    @SuppressWarnings("unused")
    @RequirePOST
    public void doSaveTotpConfig(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.READ);
        LOGGER.fine("Saving user TOTP configuration");
        
        net.sf.json.JSONObject json = req.getSubmittedForm();
        String redirectUrl = req.getContextPath() + "./";
        User user = User.current();
        
        try {
            if (user != null) {
                String verificationCode = json.getString("totpVerificationCode");
                
                if (verificationCode == null || verificationCode.trim().isEmpty()) {
                    throw new Exception("Verification code is required");
                }
                
                int code = Integer.parseInt(verificationCode.trim());
                MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
                
                // Validate the TOTP code
                if (MoTotpUtil.validateTotpCode(totpConfig.getSecretKey().getPlainText(), code)) {
                    totpConfig.setConfigured(true);
                    user.save();
                    
                    // Set session as authenticated
                    HttpSession session = req.getSession(false);
                    if (session != null) {
                        redirectUrl = (String) session.getAttribute("tfaRelayState");
                        session.removeAttribute("tfaRelayState");
                        session.setAttribute(user.getId() + SESSION_2FA_VERIFICATION.getKey(), "true");
                        userAuthenticationStatus.put(user.getId(), true);
                    }
                    
                    LOGGER.fine("TOTP configuration saved successfully for user: " + user.getId());
                } else {
                    LOGGER.warning("Invalid TOTP code provided by user: " + user.getId());
                    throw new Exception("Invalid verification code. Please try again.");
                }
            }
            
            if (redirectUrl == null) {
                redirectUrl = Jenkins.get().getRootUrl();
            }
            
            LOGGER.fine("Redirecting user to " + redirectUrl);
            FormApply.success(redirectUrl).generateResponse(req, rsp, null);
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid TOTP code format: " + e.getMessage());
            throw new Exception("Invalid verification code format. Please enter a valid 6-digit code.");
        } catch (Exception e) {
            LOGGER.severe("Error in saving TOTP configuration: " + e.getMessage());
            throw new Exception("Cannot save TOTP configuration: " + e.getMessage());
        }
    }

    /**
     * Reset TOTP configuration
     */
    @SuppressWarnings("unused")
    @RequirePOST
    public void doReset(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.READ);
        try {
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            totpConfig.setSecretKey(Secret.fromString(""));
            totpConfig.setConfigured(false);
            LOGGER.fine("Resetting the TOTP authentication method for user: " + user.getId());
            user.save();
        } catch (Exception e) {
            LOGGER.severe("Error in resetting the TOTP configuration: " + e.getMessage());
        }
        FormApply.success(req.getReferer()).generateResponse(req, rsp, null);
    }

    @Override
    public UserPropertyDescriptor getDescriptor() {
        return new MoTotpConfig.DescriptorImpl();
    }

    @Extension
    public static class DescriptorImpl extends UserPropertyDescriptor {
        public DescriptorImpl() {
            super(MoTotpConfig.class);
        }

        public String getContextPath() {
            return MoUserAuth.getContextPath();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new MoTotpConfig(Secret.fromString(""), false);
        }

        @SuppressFBWarnings(value = "NP_NONNULL_RETURN_VIOLATION", justification = "Intentionally returning null to hide from UI")
        @Override
        public String getDisplayName() {
            return null;
        }

        @SuppressWarnings("unused")
        public Boolean showInUserProfile() {
            return moPluginSettings.getOrDefault(ENABLE_2FA_FOR_ALL_USERS.getKey(), false)
                    && MoGlobalConfig.get().isEnableTotpAuthentication();
        }

        @SuppressWarnings("unused")
        public String getUserId() {
            User currentUser = User.current();
            if (currentUser == null) {
                return "";
            }
            return currentUser.getId();
        }
    }
}

