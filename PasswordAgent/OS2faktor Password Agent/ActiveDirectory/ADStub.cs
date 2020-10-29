using Serilog;
using System.DirectoryServices.AccountManagement;

namespace OS2faktor_Password_Agent
{
    public class ADStub
    {
        private static string adUrl = Settings.GetStringValue("adUrl");
        private static string adRoot = Settings.GetStringValue("adRoot");
        private static string adUsername = Settings.GetStringValue("adUsername");
        private static string adPassword = Settings.GetStringValue("adPassword");
        private static bool adRemoteEnabled = !string.IsNullOrEmpty(adUrl) && !string.IsNullOrEmpty(adRoot) && !string.IsNullOrEmpty(adUsername) && !string.IsNullOrEmpty(adPassword);

        public ILogger Logger { get; set; }

        public bool ValidatePassword(string userId, string password)
        {
            using (PrincipalContext ctx = GetPrincipalContext())
            {
                return ctx.ValidateCredentials(userId, password);
            }
        }

        public bool ChangePassword(string userId, string newPassword)
        {
            using (PrincipalContext ctx = GetPrincipalContext())
            {
                using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, userId))
                {
                    if (user != null)
                    {
                        try
                        {
                            if (!adRemoteEnabled)
                            {
                                user.SetPassword(newPassword);
                            }
                            else
                            {
                                Logger.Information("Cannot change password while outside the domain");
                            }

                            return true;
                        }
                        catch (PasswordException ex)
                        {
                            Logger.Information(ex, "Error during password validation");

                            return false;
                        }
                    }
                    else
                    {
                        Logger.Warning("Cannot find user '" + userId + "' in Active Directory");
                    }
                }
            }

            return false;
        }

        private PrincipalContext GetPrincipalContext()
        {
            if (adRemoteEnabled)
            {
                return new PrincipalContext(ContextType.Domain, adUrl, adRoot, adUsername, adPassword);
            }

            return new PrincipalContext(ContextType.Domain);
        }
    }
}
