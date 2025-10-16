package io.jenkins.plugins.twofactor.jenkins;

import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.util.FormApply;
import io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig.MoOtpOverEmailConfig;
import io.jenkins.plugins.twofactor.jenkins.tfaMethodsConfig.MoSecurityQuestionConfig;
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

    @Override
    public Descriptor<MoUserManagement> getDescriptor() {
        Jenkins jenkins = Jenkins.get();
        return (Descriptor<MoUserManagement>) jenkins.getDescriptorOrDie(getClass());
    }
}
