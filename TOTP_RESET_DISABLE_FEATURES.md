# TOTP Reset & Disable 2FA Features

## Tổng quan
Đã cập nhật plugin miniOrange Two-Factor Authentication để hỗ trợ đầy đủ các chức năng reset và disable 2FA cho TOTP (Time-based One-Time Password).

## Các tính năng đã triển khai

### 1. Reset 2FA cho User cá nhân (User Management)

**File:** `src/main/java/io/jenkins/plugins/twofactor/jenkins/MoUserManagement.java`

#### Phương thức `doResetUser2FA()`
- **Chức năng**: Reset toàn bộ cấu hình 2FA của một user
- **Quyền yêu cầu**: `Jenkins.ADMINISTER`
- **Các bước thực hiện**:
  1. Xóa cấu hình Security Questions
  2. Xóa cấu hình OTP Over Email
  3. Xóa cấu hình TOTP (secret key và QR code)
  4. Đánh dấu tất cả các phương thức là chưa cấu hình
  5. Lưu thay đổi vào user profile

#### Phương thức `doDisableUser2FA()`
- **Chức năng**: Disable 2FA cho user (tương tự reset)
- **Quyền yêu cầu**: `Jenkins.ADMINISTER`
- **Lưu ý**: Disable và Reset có cùng chức năng - xóa toàn bộ cấu hình 2FA

#### Phương thức `doEnableUser2FA()`
- **Chức năng**: Enable 2FA cho user
- **Quyền yêu cầu**: `Jenkins.ADMINISTER`
- **Lưu ý**: Chỉ đánh dấu 2FA được enable, user sẽ phải cấu hình phương thức khi đăng nhập lần sau

#### Phương thức `get2FAStatus()`
- **Chức năng**: Lấy trạng thái cấu hình 2FA của user
- **Trả về**: String mô tả các phương thức đã cấu hình
  - "Not Configured" - Chưa cấu hình
  - "Configured: Security Questions" - Đã cấu hình Security Questions
  - "Configured: Email OTP" - Đã cấu hình OTP qua Email
  - "Configured: TOTP" - Đã cấu hình TOTP
  - Hoặc kết hợp nhiều phương thức

### 2. Reset TOTP cho User tự reset (User Config)

**File:** `src/main/java/io/jenkins/plugins/twofactor/jenkins/tfaMethodsConfig/MoTotpConfig.java`

#### Phương thức `doReset()`
- **Chức năng**: User tự reset cấu hình TOTP của mình
- **Quyền yêu cầu**: `Jenkins.READ` (user đã đăng nhập)
- **Các bước thực hiện**:
  1. Xóa secret key
  2. Đánh dấu TOTP là chưa cấu hình
  3. Lưu thay đổi
  4. Redirect về trang cấu hình

### 3. Giao diện User Management

**File:** `src/main/resources/io/jenkins/plugins/twofactor/jenkins/MoUserManagement/index.jelly`

#### Cập nhật UI:
- **Thêm cột "2FA Status"**: Hiển thị trạng thái cấu hình 2FA của từng user
- **Action Links hoạt động thực tế**:
  - **Enable 2FA**: Form POST đến `enableUser2FA`
  - **Disable 2FA**: Form POST đến `disableUser2FA` (có confirm dialog)
  - **Reset 2FA**: Form POST đến `resetUser2FA` (có confirm dialog)
- **CSRF Protection**: Sử dụng `f:form` tag để tự động thêm CSRF token
- **Confirmation Dialogs**: 
  - Disable: "Are you sure you want to disable 2FA for {userId}?"
  - Reset: "Are you sure you want to reset 2FA for {userId}? This will clear all their 2FA configurations."

#### Success/Error Messages:
- Success banner: Hiển thị khi action thành công
- Error banner: Hiển thị khi action thất bại
- Auto-hide sau 5 giây

### 4. JavaScript Updates

**File:** `src/main/resources/io/jenkins/plugins/twofactor/jenkins/assets/JS/moUserManagement.js`

#### Cập nhật:
- **Loại bỏ premium banner cho individual actions**: Chỉ hiển thị cho bulk actions
- **Thêm success/error banner handling**: Tự động hiển thị và ẩn thông báo
- **URL parameter checking**: Kiểm tra `?success` hoặc `?error` trong URL

## Cách sử dụng

### Cho Admin (User Management):

1. **Truy cập User Management**:
   - Vào **Manage Jenkins** → **2FA Global Configurations**
   - Click vào tab **"Users"** hoặc truy cập `/userManagement`

2. **Reset 2FA cho user**:
   - Tìm user trong danh sách
   - Click vào link **"Reset 2FA"**
   - Confirm trong dialog
   - User sẽ phải cấu hình lại 2FA khi đăng nhập lần sau

3. **Disable 2FA cho user**:
   - Tìm user trong danh sách
   - Click vào link **"Disable 2FA"**
   - Confirm trong dialog
   - User sẽ không cần 2FA nữa (nếu global config cho phép)

4. **Enable 2FA cho user**:
   - Tìm user trong danh sách
   - Click vào link **"Enable 2FA"**
   - User sẽ phải cấu hình 2FA khi đăng nhập lần sau

5. **Xem trạng thái 2FA**:
   - Cột "2FA Status" hiển thị các phương thức đã cấu hình
   - "Not Configured": Chưa cấu hình
   - "Configured: TOTP": Đã cấu hình TOTP
   - Có thể hiển thị nhiều phương thức cùng lúc

### Cho User (Self-Service):

1. **Reset TOTP của chính mình**:
   - Vào **User Profile** → **Configure**
   - Scroll đến phần **"TOTP Authenticator"**
   - Click nút **"Reset"**
   - Confirm trong dialog
   - Cấu hình lại TOTP bằng cách quét QR code mới

2. **Reconfigure TOTP**:
   - Sau khi reset, user có thể cấu hình lại ngay
   - Hoặc đợi đến lần đăng nhập tiếp theo

## Luồng hoạt động

### Reset 2FA Flow:
```
Admin clicks "Reset 2FA" 
  → Confirm dialog appears
  → POST request to /userManagement/resetUser2FA
  → MoUserManagement.doResetUser2FA() executes
  → Clear all 2FA configs (Security Questions, Email OTP, TOTP)
  → Save user profile
  → Redirect back with success message
  → User must reconfigure 2FA on next login
```

### Disable 2FA Flow:
```
Admin clicks "Disable 2FA"
  → Confirm dialog appears
  → POST request to /userManagement/disableUser2FA
  → MoUserManagement.doDisableUser2FA() executes
  → Clear all 2FA configs (same as reset)
  → Save user profile
  → Redirect back with success message
  → User can login without 2FA (if global config allows)
```

### Enable 2FA Flow:
```
Admin clicks "Enable 2FA"
  → POST request to /userManagement/enableUser2FA
  → MoUserManagement.doEnableUser2FA() executes
  → Log the action
  → Redirect back with success message
  → User must configure 2FA on next login
```

### User Self-Reset TOTP Flow:
```
User clicks "Reset" in TOTP config
  → Confirm dialog appears
  → POST request to /totpConfig/doReset
  → MoTotpConfig.doReset() executes
  → Clear TOTP secret key
  → Mark as not configured
  → Save user profile
  → Redirect back to config page
  → User can reconfigure immediately
```

## Security Considerations

1. **Permission Checks**:
   - Admin actions require `Jenkins.ADMINISTER` permission
   - User self-service requires `Jenkins.READ` permission (authenticated user)

2. **CSRF Protection**:
   - All forms use `f:form` tag with automatic CSRF token
   - `@RequirePOST` annotation on all action methods

3. **Confirmation Dialogs**:
   - Prevent accidental resets/disables
   - Clear warning messages about consequences

4. **Audit Logging**:
   - All actions are logged with user ID
   - Log level: INFO for successful actions, SEVERE for errors

5. **Data Cleanup**:
   - Secret keys are properly cleared using `Secret.fromString("")`
   - All configuration flags are reset to false
   - User profile is saved atomically

## Testing Checklist

- [ ] Admin can reset 2FA for any user
- [ ] Admin can disable 2FA for any user
- [ ] Admin can enable 2FA for any user
- [ ] User can reset their own TOTP configuration
- [ ] 2FA Status column shows correct information
- [ ] Confirmation dialogs appear for destructive actions
- [ ] Success/error messages display correctly
- [ ] CSRF tokens are included in all forms
- [ ] Permission checks work correctly
- [ ] Audit logs are written correctly
- [ ] User must reconfigure after reset
- [ ] QR code regenerates after reset
- [ ] Multiple 2FA methods can coexist
- [ ] Reset clears all 2FA methods

## Files Modified

### Java Files:
1. `src/main/java/io/jenkins/plugins/twofactor/jenkins/MoUserManagement.java`
   - Added: `doResetUser2FA()`, `doDisableUser2FA()`, `doEnableUser2FA()`, `get2FAStatus()`
   - Imports: Added `Secret`, `MoTotpConfig`

### Jelly Files:
1. `src/main/resources/io/jenkins/plugins/twofactor/jenkins/MoUserManagement/index.jelly`
   - Added: 2FA Status column
   - Updated: Action links to use `f:form` with POST
   - Added: Confirmation dialogs
   - Added: Success/error banners

### JavaScript Files:
1. `src/main/resources/io/jenkins/plugins/twofactor/jenkins/assets/JS/moUserManagement.js`
   - Updated: Premium banner logic (only for bulk actions)
   - Added: Success/error banner handling
   - Added: URL parameter checking

## Build Information

**Build Command**: `mvn clean compile hpi:hpi -DskipTests`

**Output**: `target/miniorange-two-factor.hpi`

**Status**: ✅ BUILD SUCCESS

**Version**: 1.0.11-SNAPSHOT

## Next Steps

1. Install the plugin in Jenkins test environment
2. Test all reset/disable/enable scenarios
3. Verify TOTP reconfiguration works after reset
4. Check audit logs for all actions
5. Test with multiple users and multiple 2FA methods
6. Verify permission checks work correctly
7. Test CSRF protection
8. Verify UI displays correctly in different browsers

