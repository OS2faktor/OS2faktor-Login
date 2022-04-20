using Microsoft.Win32;
using Serilog;
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
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
                String settingsPath = @"SOFTWARE\DigitalIdentity\OS2faktorLogin";

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
                        string domain;
                        string baseUrl;
                        string clientID;
                        string clientApiKey;

                        try
                        {
                            domain = (string)settingsKey.GetValue("os2faktorUserDomain");
                            baseUrl = (string)settingsKey.GetValue("os2faktorBaseUrl");
                            clientID = (string)settingsKey.GetValue("clientID");
                            clientApiKey = (string)settingsKey.GetValue("clientApiKey");

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
                        Log.Verbose("Setting clientID to: " + clientID);
                        Log.Verbose("Setting clientApiKey to: " + clientApiKey);
                        Log.Verbose("Setting domain to: " + domain);
                        HttpClient client = new HttpClient();
                        client.DefaultRequestHeaders.Add("clientID", clientID);
                        client.DefaultRequestHeaders.Add("apiKey", clientApiKey);

                        // Set parameters for the call domain/username/password/newPassword
                        var parameters = new Dictionary<string, string> {
                            { "domain", domain },
                            { "username", args[0] },
                            { "oldPassword", args[1] },
                            { "newPassword", args[2] }
                        };

                        var content = new FormUrlEncodedContent(parameters);

                        // Call the service and wait for the response
                        try
                        {
                            // Create URL from configured baseURL
                            string url = (baseUrl.EndsWith("/") ? baseUrl : (baseUrl + "/")) + "api/client/changePassword";

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
    }
}
