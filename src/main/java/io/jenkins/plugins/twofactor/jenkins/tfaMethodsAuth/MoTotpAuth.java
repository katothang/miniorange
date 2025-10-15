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
package io.jenkins.plugins.twofactor.jenkins.tfaMethodsAuth;

import static io.jenkins.plugins.twofactor.jenkins.MoFilter.userAuthenticationStatus;
import static io.jenkins.plugins.twofactor.jenkins.MoUserAuth.allow2FaAccessAndRedirect;

import hudson.model.Action;
import hudson.model.User;
import hudson.util.FormApply;
import io.jenkins.plugins.twofactor.constants.MoPluginUrls;
import io.jenkins.plugins.twofactor.jenkins.MoUserAuth;
import io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig.MoTotpConfig;
import io.jenkins.plugins.twofactor.jenkins.util.MoTotpUtil;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class MoTotpAuth implements Action {

    private static final Logger LOGGER = Logger.getLogger(MoTotpAuth.class.getName());
    public Map<String, Boolean> showWrongCredentialWarning = new HashMap<>();

    private final User user;

    public MoTotpAuth() {
        user = User.current();
    }

    @Override
    public String getIconFileName() {
        return "";
    }

    @Override
    public String getDisplayName() {
        return MoPluginUrls.Urls.MO_TOTP_AUTH.getUrl();
    }

    @Override
    public String getUrlName() {
        return MoPluginUrls.Urls.MO_TOTP_AUTH.getUrl();
    }

    public String getContextPath() {
        return MoUserAuth.getContextPath();
    }

    @SuppressWarnings("unused")
    public String getUserId() {
        return user != null ? user.getId() : "";
    }

    @SuppressWarnings("unused")
    public boolean isUserAuthenticatedFromTfa() {
        return userAuthenticationStatus.getOrDefault(user.getId(), false);
    }

    @SuppressWarnings("unused")
    public boolean getShowWrongCredentialWarning() {
        return showWrongCredentialWarning.getOrDefault(user.getId(), false);
    }

    public boolean isTotpConfigured() {
        MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
        return totpConfig != null && totpConfig.isConfigured();
    }

    /**
     * Get QR code data URI for TOTP setup
     * This is used when user hasn't configured TOTP yet
     */
    @SuppressWarnings("unused")
    public String getQRCodeDataUri() {
        try {
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            if (totpConfig == null) {
                return "";
            }
            
            // Generate new secret key if not exists
            if (totpConfig.getSecretKey() == null || totpConfig.getSecretKey().getPlainText().isEmpty()) {
                String newSecretKey = MoTotpUtil.generateSecretKey();
                totpConfig.setSecretKey(hudson.util.Secret.fromString(newSecretKey));
                user.save();
            }
            
            String accountName = user.getId();
            String issuer = "Jenkins";
            return MoTotpUtil.generateQRCodeDataUri(totpConfig.getSecretKey().getPlainText(), accountName, issuer);
        } catch (Exception e) {
            LOGGER.severe("Error generating QR code for user " + user.getId() + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Get the secret key in plain text for manual entry
     */
    @SuppressWarnings("unused")
    public String getSecretKeyPlainText() {
        try {
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            if (totpConfig == null || totpConfig.getSecretKey() == null) {
                return "";
            }
            return totpConfig.getSecretKey().getPlainText();
        } catch (Exception e) {
            LOGGER.warning("Error getting secret key for user " + user.getId() + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Validate TOTP code and authenticate user
     */
    @SuppressWarnings("unused")
    @RequirePOST
    public void doValidateTotp(StaplerRequest req, StaplerResponse rsp) throws Exception {
        Jenkins.get().checkPermission(Jenkins.READ);
        
        net.sf.json.JSONObject json = req.getSubmittedForm();
        HttpSession session = req.getSession(false);
        String redirectUrl = Jenkins.get().getRootUrl();
        
        LOGGER.fine("Authenticating user TOTP code");
        
        try {
            if (user == null) {
                LOGGER.warning("User is null during TOTP authentication");
                return;
            }
            
            String totpCodeStr = json.getString("totpCode");
            if (totpCodeStr == null || totpCodeStr.trim().isEmpty()) {
                LOGGER.warning("TOTP code is empty for user: " + user.getId());
                redirectUrl = "./";
                showWrongCredentialWarning.put(user.getId(), true);
                FormApply.success(redirectUrl).generateResponse(req, rsp, null);
                return;
            }
            
            int totpCode = Integer.parseInt(totpCodeStr.trim());
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            
            if (totpConfig == null || totpConfig.getSecretKey() == null) {
                LOGGER.warning("TOTP config not found for user: " + user.getId());
                redirectUrl = "./";
                showWrongCredentialWarning.put(user.getId(), true);
                FormApply.success(redirectUrl).generateResponse(req, rsp, null);
                return;
            }
            
            // Validate TOTP code
            boolean isValid = MoTotpUtil.validateTotpCode(totpConfig.getSecretKey().getPlainText(), totpCode);
            
            if (isValid) {
                LOGGER.fine("TOTP code is valid for user: " + user.getId());
                
                // If this is first time setup, mark as configured
                if (!totpConfig.isConfigured()) {
                    totpConfig.setConfigured(true);
                    user.save();
                    LOGGER.fine("TOTP configured for user: " + user.getId());
                }
                
                // Authenticate user
                redirectUrl = allow2FaAccessAndRedirect(session, user, showWrongCredentialWarning);
            } else {
                LOGGER.warning("Invalid TOTP code for user: " + user.getId());
                redirectUrl = "./";
                showWrongCredentialWarning.put(user.getId(), true);
            }
            
            if (redirectUrl == null) {
                redirectUrl = Jenkins.get().getRootUrl();
            }
            
            LOGGER.fine("Redirecting user " + user.getId() + " from MoTotpAuth to " + redirectUrl);
            FormApply.success(redirectUrl).generateResponse(req, rsp, null);
            
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid TOTP code format for user " + user.getId() + ": " + e.getMessage());
            redirectUrl = "./";
            showWrongCredentialWarning.put(user.getId(), true);
            FormApply.success(redirectUrl).generateResponse(req, rsp, null);
        } catch (Exception e) {
            LOGGER.severe("Exception while authenticating TOTP for user " + user.getId() + ": " + e.getMessage());
            throw e;
        }
    }
}

