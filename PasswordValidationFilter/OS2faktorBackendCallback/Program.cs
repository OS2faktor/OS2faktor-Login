using Microsoft.Win32;
using Serilog;
using System.Net;
using System.Security.Cryptography;
using System.Security.Principal;

namespace OS2faktorBackendCallback
{
    internal class Program
    {
        static async Task Main(string[] args)
        {
            try
            {
                // Open up the settings, we need these to run the program so without them it just closes
                string settingsPath = @"SOFTWARE\DigitalIdentity\OS2faktorPasswordFilter";

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

                            object logPathObj = settingsKey.GetValue("CallbackLogPath");
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
                            .MinimumLevel.Verbose()
                            .WriteTo.File(logPath, rollingInterval: RollingInterval.Infinite)
                            .CreateLogger();
                        }

                        Log.Verbose("Logging initialized, CreateSession invoked.");


                        // Check that the args are correct
                        if (args.Length != 2)
                        {
                            Log.Error("Incorrect amount of args ({0}) passed to CreateSession", args.Length);
                            return;
                        }

                        // Make configurable or remove (this is for self signed certificates)
                        ServicePointManager.ServerCertificateValidationCallback += (sender, cert, chain, sslPolicyErrors) => true;

                        var handler = new HttpClientHandler()
                        {
                            ServerCertificateCustomValidationCallback = HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
                        };

                        // Create HttpClient and set RequestHeaders
                        HttpClient client = new HttpClient(handler);
                        client.DefaultRequestHeaders.Add("apiKey", clientApiKey);


                        // Create body of message
                        // {
                        //   "accountName": "xxxx",
                        //   "password": "xxxx",
                        //   "version": "xxxx"
                        // }
                        string body = "{\"accountName\":\"" + args[0] + "\",\"password\":\"" + getPassword(args[1]) + "\"";
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
                            string url = (baseUrl.EndsWith("/") ? baseUrl : (baseUrl + "/")) + "api/password/filter/v1/validate";

                            Log.Verbose("Calling url: " + url);

                            HttpResponseMessage response = await client.PostAsync(url, content);

                            Log.Verbose("Response recieved");

                            // We can expect 200, 400, 409 and 500 to be returned.
                            // We deny password changes on 409, if anything else goes wrong (400, 500)
                            // or the validation is a success (200) then we still allow the password change.
                            if (response.StatusCode == HttpStatusCode.Conflict)
                            {
                                Log.Verbose("Response was 409 Conflict");
                                Console.Out.Write(-1);
                            }
                            else 
                            {
                                Log.Verbose($"Response was {response.StatusCode}, allowing password change.");
                                Console.Out.Write(1);
                            }
                            return;
                        }
                        catch (Exception ex)
                        {
                            Log.Error(ex, "Could not fetch temporary session key from os2faktor.");
                            return;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                try
                {
                    Log.Error(ex, "Failed to run CreateSession command");
                }
                catch (Exception)
                {
                    ;
                }
            }
        }

        private static string getPassword(string encodedPassword)
        {
            byte[] bytes = ProtectedData.Unprotect(Convert.FromBase64String(encodedPassword), null, DataProtectionScope.CurrentUser);
            string nonEncPass = System.Text.Encoding.Unicode.GetString(bytes);
            return nonEncPass;
        }
    }
}
