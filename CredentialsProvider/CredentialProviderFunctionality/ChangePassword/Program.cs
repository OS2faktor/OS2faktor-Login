using Microsoft.Win32;
using Serilog;
using System;
using System.Net.Http;
using System.Security.Cryptography;
using System.Threading.Tasks;

namespace ChangePassword
{
    class Program
    {
        static async Task Main(string[] args)
        {
            try
            {
                // Open up the settings, we need these to run the program so without them it just closes
                string settingsPath = @"SOFTWARE\DigitalIdentity\OS2faktorLogin";

                using (RegistryKey rootKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
                {
                    using (RegistryKey settingsKey = rootKey.OpenSubKey(settingsPath, false))
                    {
                        if (settingsKey == null || !(settingsKey.GetValueNames().Length > 0))
                        {
                            return;
                        }

                        // Settings avaliable, continue process
                        string logPath = null;
                        string baseUrl;
                        string clientApiKey;
                        string version;

                        try
                        {
                            baseUrl = (string)settingsKey.GetValue("os2faktorBaseUrl");
                            clientApiKey = (string)settingsKey.GetValue("clientApiKey");
                            version = (string)settingsKey.GetValue("version");

                            object logPathObj = settingsKey.GetValue("ChangePasswordLogPath");
                            if (logPathObj != null)
                            {
                                logPath = (string)logPathObj;
                            }
                        }
                        catch (Exception)
                        {
                            return; // No logger is set up yet can't log failure
                        }

                        // Init logger
                        if (logPath != null && logPath.Length > 0)
                        {
                            Log.Logger = new LoggerConfiguration()
                            .MinimumLevel.Error()
                            .WriteTo.File(logPath, rollingInterval: RollingInterval.Infinite)
                            .CreateLogger();
                        }

                        Log.Verbose("Logging initialized, ChangePassword invoked.");

                        // Check that the args are correct
                        if (args.Length != 3)
                        {
                            Log.Error("Incorrect amount of args ({0}) passed to ChangePassword", args.Length);
                            return;
                        }

                        // Make configurable or remove (this is for self signed certificates)
                        //ServicePointManager.ServerCertificateValidationCallback += (sender, cert, chain, sslPolicyErrors) => true;

                        // Create HttpClient and set RequestHeaders
                        HttpClient client = new HttpClient();
                        client.DefaultRequestHeaders.Add("apiKey", clientApiKey);

                        // {
                        //   "username": "xxxx",
                        //   "oldPassword": "xxxx",
                        //   "newPassword": "xxxx",
                        //   "version": "xxxx"
                        // }
                        string body = "{\"username\":\"" + args[0] + "\",\"oldPassword\":\"" + getPassword(args[1]) + "\",\"newPassword\":\"" + getPassword(args[2]) + "\"";
                        if (version != null)
                        {
                            string versionParameter = version.Replace(".", "").Trim();
                            if (!String.IsNullOrWhiteSpace(versionParameter))
                            {
                                body += ",\"version\":\"" + versionParameter + "\"";
                            }
                        }
                        body += "}";
                        HttpContent content = new StringContent(body, System.Text.Encoding.UTF8, "application/json");


                        // Call the service and wait for the response
                        try
                        {
                            // Create URL from configured baseURL
                            string url = (baseUrl.EndsWith("/") ? baseUrl : (baseUrl + "/")) + "api/client/changePasswordWithBody";

                            HttpResponseMessage response = await client.PostAsync(url, content);
                            if (response.IsSuccessStatusCode)
                            {
                                Log.Verbose("Successfully changed password");
                            }
                            else
                            {
                                Log.Error("Could not change password in os2faktor. Error: {0} '{1}'", response.StatusCode, response.ReasonPhrase);
                                return;
                            }
                        }
                        catch (System.Exception ex)
                        {
                            Log.Error(ex, "Error propegating change password to os2faktor.");
                            return;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                try
                {
                    Log.Error(ex, "Failed to run ChangePassword command");
                }
                catch (Exception)
                {
                    ;
                }
            }
        }

        private static string getPassword(string encodedPassword)
        {
            string encodedPass = System.Text.Encoding.UTF8.GetString(Convert.FromBase64String(encodedPassword));
            byte[] bytes = ProtectedData.Unprotect(Convert.FromBase64String(encodedPassword), null, DataProtectionScope.CurrentUser);
            string nonEncPass = System.Text.Encoding.Unicode.GetString(bytes);
            return nonEncPass;
        }
    }
}
