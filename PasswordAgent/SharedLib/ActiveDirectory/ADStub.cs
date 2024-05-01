using Serilog;
using System;
using System.DirectoryServices.AccountManagement;
using System.Management.Automation;

namespace OS2faktor
{
    public class ADStub
    {
        private ILogger logger;
        private Settings settings;

        public ADStub(ILogger logger, Settings settings)
        {
            this.logger = logger;
            this.settings = settings;
        }

        public bool ValidatePassword(string userId, string password)
        {
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
                    if (settings.GetBooleanValue("useNegotiation"))
                    {
                        return ctx.ValidateCredentials(userId, password, ContextOptions.Negotiate);
                    }

                    return ctx.ValidateCredentials(userId, password);
                }
            }
            catch (System.Exception ex)
            {
                logger.Error(ex, "Error during validatePassword");

                return false;
            }
        }

        public ChangePasswordResponse ChangePassword(string userId, string newPassword)
        {
            return ChangePassword(userId, newPassword, false);
        }

        public ChangePasswordResponse ChangePassword(string userId, string newPassword, bool forceChange)
        {
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
                    using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, IdentityType.SamAccountName, userId))
                    {
                        if (user != null)
                        {
                            // Note that this ONLY works if you are domain joined, and if you are either a domain admin, or
                            // have been granted the Change Password right (and if not a domain admin, the username/password must be supplied,
                            // for reasons unknown)
                            user.SetPassword(newPassword);

                            try
                            {
                                user.UnlockAccount();
                            }
                            catch (System.Exception ex)
                            {
                                logger.Warning(ex, "Attempting to unlock account (lockoutTime = 0) during password reset failed for " + userId + ". Password reset succeeded, but if they account was locked, it is still locked");
                            }

                            if (forceChange)
                            {
                                try
                                {
                                    user.ExpirePasswordNow();
                                }
                                catch (System.Exception ex)
                                {
                                    logger.Warning(ex, "Attempting expire password (force user to change password on next logon) during password reset failed for " + userId + ". Password reset succeeded, but they won't be forced to change password");
                                }
                            }

                            return new ChangePasswordResponse()
                            {
                                Success = true
                            };
                        }
                        else
                        {
                            logger.Warning("Cannot find user '" + userId + "' in Active Directory");
                        }
                    }
                }

                return new ChangePasswordResponse()
                {
                    Success = true,
                    Message = "Could not find user: " + userId
                };
            }
            catch (System.Exception ex)
            {
                logger.Error(ex, "Error during setPassword");

                return new ChangePasswordResponse()
                {
                    Success = false,
                    Message = ex.ToString()
                };
            }
        }

        public ChangePasswordResponse UnlockAccount(string userId)
        {
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
                    using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, IdentityType.SamAccountName, userId))
                    {
                        if (user != null)
                        {
                            user.UnlockAccount();

                            return new ChangePasswordResponse()
                            {
                                Success = true
                            };
                        }
                        else
                        {
                            logger.Warning("Cannot find user '" + userId + "' in Active Directory");
                        }
                    }
                }

                return new ChangePasswordResponse()
                {
                    Success = true,
                    Message = "Could not find user: " + userId
                };
            }
            catch (System.Exception ex)
            {
                logger.Information(ex, "Error during unlock account");

                return new ChangePasswordResponse()
                {
                    Success = false,
                    Message = ex.ToString()
                };
            }
        }

        public ChangePasswordResponse RunPasswordExpiresScript(string userId)
        {
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
                    using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, IdentityType.SamAccountName, userId))
                    {
                        if (user != null)
                        {
                            RunScript(userId);

                            return new ChangePasswordResponse()
                            {
                                Success = true
                            };
                        }
                        else
                        {
                            logger.Warning("Cannot find user '" + userId + "' in Active Directory");
                        }
                    }
                }

                return new ChangePasswordResponse()
                {
                    Success = true,
                    Message = "Could not find user: " + userId
                };
            }
            catch (System.Exception ex)
            {
                logger.Information(ex, "Error during run password expires script");

                return new ChangePasswordResponse()
                {
                    Success = false,
                    Message = ex.ToString()
                };
            }
        }

        public void RunScript(string sAMAccountName)
        {
            var passwordExpiresPowerShell = settings.GetStringValue("passwordExpiresPowerShell");
            if (!string.IsNullOrEmpty(passwordExpiresPowerShell))
            {
                string script = System.IO.File.ReadAllText(passwordExpiresPowerShell);

                if (!string.IsNullOrEmpty(script))
                {
                    using (PowerShell powershell = PowerShell.Create())
                    {
                        script = script + "\n\n" +
                        "$ppArg1=\"" + sAMAccountName + "\"\n";

                        script += "Invoke-Method -SAMAccountName $ppArg1 -Name $ppArg2\n";

                        powershell.AddScript(script);
                        powershell.Invoke();
                    }
                }
            }
        }

        private PrincipalContext GetPrincipalContext()
        {
            return new PrincipalContext(ContextType.Domain);
        }
    }
}
