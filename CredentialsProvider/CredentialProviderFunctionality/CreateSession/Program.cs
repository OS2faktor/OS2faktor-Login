using Microsoft.Win32;
using Serilog;
using System;
using System.Net.Http;
using System.Threading.Tasks;
using System.Security.Principal;
using System.Security.Cryptography;
using System.Net;
using System.Text;
using System.Text.Json;

namespace CreateSession
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

                            object logPathObj = settingsKey.GetValue("CreateSessionLogPath");
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
                        Log.Verbose($"Running as: {WindowsIdentity.GetCurrent().Name}");

                        // Check that the args are correct
                        if (args.Length != 2)
                        {
                            Log.Error("Incorrect amount of args ({0}) passed to CreateSession", args.Length);
                            return;
                        }

                        // Make configurable or remove (this is for self signed certificates)
                        //ServicePointManager.ServerCertificateValidationCallback += (sender, cert, chain, sslPolicyErrors) => true;

                        // Create HttpClient and set RequestHeaders
                        HttpClient client = new HttpClient();
                        client.DefaultRequestHeaders.Add("apiKey", clientApiKey);

                        // Create body of message
                        // {
                        //   "username": "xxxx",
                        //   "password": "xxxx",
                        //   "version": "xxxx",
                        //   "base64": "true"
                        // }
                        string versionStr = version == null ? "0" : version.Replace(".", "").Trim();
                        var body = new
                        {
                            username = args[0],
                            password = getPassword(args[1]),
                            version = !String.IsNullOrWhiteSpace(versionStr) ? versionStr : "0",
                            base64 = false
                        };
                        string bodyString = JsonSerializer.Serialize(body);
                        HttpContent content = new StringContent(bodyString, System.Text.Encoding.UTF8, "application/json");

                        // Call the service and wait for the response
                        try
                        {
                            // Create URL from configured baseURL
                            string url = (baseUrl.EndsWith("/") ? baseUrl : (baseUrl + "/")) + "api/client/loginWithBody";

                            Log.Verbose("Calling url: " + url);

                            HttpResponseMessage response = await client.PostAsync(url, content);

                            Log.Verbose("Response received");

                            if (response.IsSuccessStatusCode)
                            {
                                Log.Verbose("Response was positive, extablishing session");

                                string establishSessionUrl = await response.Content.ReadAsStringAsync();
                                Log.Verbose("Token read");

                                Log.Verbose("Sending establishSessionUrl to CredentialManager");
                                Console.Out.Write(establishSessionUrl);
                            }
                            else
                            {
                                var responseBody = await response.Content.ReadAsStringAsync();
                                Log.Error("Could not fetch temporary session key from os2faktor. Error: {0} '{1}'", response.StatusCode, responseBody);
                                Log.Information($"User: {args[0]}, Password non-zero length: {getPassword(args[1]).Length > 0}");
                                return;
                            }
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
            return System.Text.Encoding.Unicode.GetString(bytes);
        }
    }
}
