using Serilog;
using System.DirectoryServices.AccountManagement;

namespace OS2faktor
{
    public class ADStub
    {
        private ILogger logger;

        public ADStub(ILogger logger)
        {
            this.logger = logger;
        }

        public bool ValidatePassword(string userId, string password)
        {
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
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
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
                    using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, userId))
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
                                logger.Warning("Attempting to unlock account (lockoutTime = 0) during password reset failed for " + userId + ". Password reset succeeded, but if they account was locked, it is still locked. Cause = " + ex.Message);
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
                    Message = ex.Message
                };
            }
        }

        public ChangePasswordResponse UnlockAccount(string userId)
        {
            try
            {
                using (PrincipalContext ctx = GetPrincipalContext())
                {
                    using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, userId))
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
                    Message = ex.Message
                };
            }
        }

        private PrincipalContext GetPrincipalContext()
        {
            return new PrincipalContext(ContextType.Domain);
        }
    }
}
