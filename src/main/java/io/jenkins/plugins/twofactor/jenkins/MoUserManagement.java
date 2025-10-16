package io.jenkins.plugins.twofactor.jenkins;

import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.util.FormApply;
import hudson.util.Secret;
import io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig.MoOtpOverEmailConfig;
import io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig.MoSecurityQuestionConfig;
import io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig.MoTotpConfig;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static io.jenkins.plugins.twofactor.constants.MoPluginUrls.Urls.MO_TFA_USER_MANAGEMENT;

public class MoUserManagement implements Action, Describable<MoUserManagement> {
    private static final Logger LOGGER = Logger.getLogger(MoUserManagement.class.getName());
    @Override
    public String getIconFileName() {
        return "symbol-people";
    }

    @Override
    public String getDisplayName() {
        return "user-management";
    }

    @Override
    public String getUrlName() {
        return MO_TFA_USER_MANAGEMENT.getUrl();
    }

    public Object getAllUsers(){
        return  User.getAll().toArray();
    }

    public Boolean getStatus(String userID){
        return MoGlobalConfig.get().getEnableTfa();
    }

    /**
     * Check if user is in bypass list
     */
    public Boolean isUserBypassed(User user) {
        if (user == null) {
            return false;
        }
        List<String> bypassList = MoGlobalConfig.get().getBypassUsersList();
        return bypassList.stream()
                .anyMatch(u -> u.equalsIgnoreCase(user.getId()));
    }

    /**
     * Add user to bypass 2FA list
     */
    @SuppressWarnings("unused")
    @RequirePOST
    public void doBypassUser2FA(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            String userId = req.getParameter("userId");
            if (userId == null || userId.trim().isEmpty()) {
                LOGGER.warning("User ID is empty for bypass operation");
                return;
            }

            MoGlobalConfig globalConfig = MoGlobalConfig.get();
            String currentBypassUsers = globalConfig.getBypassUsers();
            List<String> bypassList = currentBypassUsers == null || currentBypassUsers.trim().isEmpty()
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(Arrays.asList(currentBypassUsers.split("[,\\s]+")));

            // Add user if not already in list
            if (!bypassList.contains(userId)) {
                bypassList.add(userId);
                String updatedBypassUsers = String.join(",", bypassList);
                globalConfig.setBypassUsers(updatedBypassUsers);
                globalConfig.save();
                LOGGER.fine("User " + userId + " added to bypass 2FA list");
            }
        } catch (Exception e) {
            LOGGER.severe("Error in adding user to bypass 2FA list: " + e.getMessage());
        }
        FormApply.success(req.getReferer()).generateResponse(req, rsp, null);
    }

    /**
     * Remove user from bypass 2FA list (Unbypass OTP)
     */
    @SuppressWarnings("unused")
    @RequirePOST
    public void doUnbypassUser2FA(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            String userId = req.getParameter("userId");
            if (userId == null || userId.trim().isEmpty()) {
                LOGGER.warning("User ID is empty for unbypass OTP operation");
                return;
            }

            MoGlobalConfig globalConfig = MoGlobalConfig.get();
            String currentBypassUsers = globalConfig.getBypassUsers();
            List<String> bypassList = currentBypassUsers == null || currentBypassUsers.trim().isEmpty()
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(Arrays.asList(currentBypassUsers.split("[,\\s]+")));

            // Remove user from bypass list
            bypassList.removeIf(u -> u.equalsIgnoreCase(userId));
            String updatedBypassUsers = String.join(",", bypassList);
            globalConfig.setBypassUsers(updatedBypassUsers);
            globalConfig.save();
            LOGGER.fine("User " + userId + " removed from bypass 2FA list");
        } catch (Exception e) {
            LOGGER.severe("Error in removing user from bypass 2FA list: " + e.getMessage());
        }
        FormApply.success(req.getReferer()).generateResponse(req, rsp, null);
    }

    /**
     * Reset 2FA configuration for a user
     */
    @SuppressWarnings("unused")
    @RequirePOST
    public void doResetUser2FA(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            String userId = req.getParameter("userId");
            if (userId == null || userId.trim().isEmpty()) {
                LOGGER.warning("User ID is empty for reset 2FA operation");
                return;
            }

            User user = User.getById(userId, false);
            if (user == null) {
                LOGGER.warning("User not found: " + userId);
                return;
            }
            // Reset TOTP
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            if (totpConfig != null) {
                totpConfig.setSecretKey(Secret.fromString(""));
                totpConfig.setConfigured(false);
            }

            user.save();
            LOGGER.fine("Reset 2FA configuration for user: " + userId);
        } catch (Exception e) {
            LOGGER.severe("Error in resetting 2FA configuration: " + e.getMessage());
        }
        FormApply.success(req.getReferer()).generateResponse(req, rsp, null);
    }

    /**
     * Get 2FA status for a user
     */
    public String get2FAStatus(User user) {
        if (user == null) {
            return "Unknown";
        }

        try {
            // Check if user is bypassed
            if (isUserBypassed(user)) {
                return "Bypassed";
            }

            // Check if any 2FA method is configured
            MoSecurityQuestionConfig securityQuestionConfig = user.getProperty(MoSecurityQuestionConfig.class);
            MoOtpOverEmailConfig otpOverEmailConfig = user.getProperty(MoOtpOverEmailConfig.class);
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);

            boolean isConfigured = false;
            if (securityQuestionConfig != null && securityQuestionConfig.isConfigured()) {
                isConfigured = true;
            }
            if (otpOverEmailConfig != null && otpOverEmailConfig.isConfigured()) {
                isConfigured = true;
            }
            if (totpConfig != null && totpConfig.isConfigured()) {
                isConfigured = true;
            }

            return isConfigured ? "Configured" : "Not Configured";
        } catch (Exception e) {
            LOGGER.warning("Error getting 2FA status for user " + user.getId() + ": " + e.getMessage());
            return "Unknown";
        }
    }

    /**
     * Get TOTP status for a user
     */
    public String getTotpStatus(User user) {
        if (user == null) {
            return "Not Configured";
        }

        try {
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            if (totpConfig != null && totpConfig.isConfigured()) {
                return "Configured";
            }
            return "Not Configured";
        } catch (Exception e) {
            LOGGER.warning("Error getting TOTP status for user " + user.getId() + ": " + e.getMessage());
            return "Not Configured";
        }
    }

    /**
     * Check if user has TOTP configured
     */
    public Boolean hasTotpConfigured(User user) {
        if (user == null) {
            return false;
        }

        try {
            MoTotpConfig totpConfig = user.getProperty(MoTotpConfig.class);
            return totpConfig != null && totpConfig.isConfigured();
        } catch (Exception e) {
            LOGGER.warning("Error checking TOTP configuration for user " + user.getId() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public Descriptor<MoUserManagement> getDescriptor() {
        Jenkins jenkins = Jenkins.get();
        return (Descriptor<MoUserManagement>) jenkins.getDescriptorOrDie(getClass());
    }
}
