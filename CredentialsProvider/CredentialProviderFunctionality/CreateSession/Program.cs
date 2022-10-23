using Microsoft.Win32;
using Serilog;
using System;
using System.Collections.Generic;
using System.DirectoryServices.AccountManagement;
using System.Net;
using System.Net.Http;
using System.Threading.Tasks;
using System.Configuration;

namespace CreateSession
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
                        string EdgeBrowserExtensionId;
                        string ChromeBrowserExtensionId;

                        try
                        {
                            domain = (string)settingsKey.GetValue("os2faktorUserDomain");
                            baseUrl = (string)settingsKey.GetValue("os2faktorBaseUrl");
                            clientID = (string)settingsKey.GetValue("clientID");
                            clientApiKey = (string)settingsKey.GetValue("clientApiKey");
                            EdgeBrowserExtensionId = (string)settingsKey.GetValue("EdgeBrowserExtensionId");
                            ChromeBrowserExtensionId = (string)settingsKey.GetValue("ChromeBrowserExtensionId");

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

                        // Check that the args are correct
                        if (args.Length != 2)
                        {
                            Log.Error("Incorrect amount of args ({0}) passed to CreateSession", args.Length);
                            return;
                        }

                        // Make configurable or remove (this is for self signed certificates)
                        //ServicePointManager.ServerCertificateValidationCallback += (sender, cert, chain, sslPolicyErrors) => true;

                        // Create HttpClient and set RequestHeaders
                        Log.Verbose("Setting clientID to: " + clientID);
                        Log.Verbose("Setting domain to: " + domain);
                        HttpClient client = new HttpClient();
                        client.DefaultRequestHeaders.Add("clientID", clientID);
                        client.DefaultRequestHeaders.Add("apiKey", clientApiKey);


                        // Set parameters for the call domain/username/password
                        var parameters = new Dictionary<string, string> {
                            { "domain", domain },
                            { "username", args[0] },
                            { "password", args[1] }
                        };

                        var content = new FormUrlEncodedContent(parameters);

                        // Call the service and wait for the response
                        try
                        {
                            // Create URL from configured baseURL
                            string url = (baseUrl.EndsWith("/") ? baseUrl : (baseUrl + "/")) + "api/client/login";

                            HttpResponseMessage response = await client.PostAsync(url, content);
                            if (response.IsSuccessStatusCode)
                            {
                                string establishSessionUrl = await response.Content.ReadAsStringAsync();

                                // Fetch the users SID to edit the registry
                                PrincipalContext context = null;
                                try
                                {
                                    context = new PrincipalContext(ContextType.Domain);
                                }
                                catch (PrincipalServerDownException)
                                {
                                    context = new PrincipalContext(ContextType.Machine);
                                }

                                using (context)
                                {
                                    using (UserPrincipal user = UserPrincipal.FindByIdentity(context, args[0]))
                                    {
                                        if (string.IsNullOrEmpty(user.Sid.Value))
                                        {
                                            Log.Error("User SID was null or empty");
                                            return;
                                        }

                                        string[] vs = Registry.Users.GetSubKeyNames();
                                        bool foundUserSubkey = false;
                                        foreach (var Subkey in vs)
                                        {
                                            if (user.Sid.Value.Equals(Subkey))
                                            {
                                                foundUserSubkey = true;
                                            }
                                        }

                                        if (!foundUserSubkey)
                                        {
                                            // User subkey not created yet.
                                            // Sleep before running rest of program to give windows time to generate Subkey
                                            Log.Warning("Sleeping. Waiting for User SubKey to be avaliable");
                                            System.Threading.Thread.Sleep(30 * 1000);

                                        }

                                        // Set Edge token
                                        string edgeKeyPath = "";
                                        try
                                        {
                                            if (string.IsNullOrEmpty(EdgeBrowserExtensionId))
                                            {
                                                throw new Exception("EdgeBrowserExtensionId was null or empty");
                                            }

                                            edgeKeyPath = user.Sid.Value + @"\Software\Policies\Microsoft\Edge\3rdparty\extensions\" + EdgeBrowserExtensionId + @"\policy";
                                            RegistryKey edgeKey = Registry.Users.CreateSubKey(edgeKeyPath);
                                            edgeKey.SetValue("token", establishSessionUrl, RegistryValueKind.String);
                                            edgeKey.SetValue("timestamp", DateTime.Now, RegistryValueKind.String);
                                            edgeKey.Close();
                                        }
                                        catch (Exception ex)
                                        {
                                            Log.Error(ex, "Could not set Token and Timestamp for EdgeExtension in registry. KeyPath: " + edgeKeyPath);
                                        }

                                        // Set Chrome token
                                        string chromeKeyPath = "";
                                        try
                                        {
                                            if (string.IsNullOrEmpty(ChromeBrowserExtensionId))
                                            {
                                                throw new Exception("ChromeBrowserExtensionId was null or empty");
                                            }

                                            chromeKeyPath = user.Sid.Value + @"\Software\Policies\Google\Chrome\3rdparty\extensions\" + ChromeBrowserExtensionId + @"\policy";
                                            RegistryKey chromeKey = Registry.Users.CreateSubKey(chromeKeyPath);
                                            chromeKey.SetValue("token", establishSessionUrl, RegistryValueKind.String);
                                            chromeKey.SetValue("timestamp", DateTime.Now, RegistryValueKind.String);
                                            chromeKey.Close();
                                        }
                                        catch (Exception ex)
                                        {
                                            Log.Error(ex, "Could not set Token and Timestamp for ChromeExtension in registry. KeyPath: " + chromeKeyPath);
                                        }
                                    }
                                }
                            }
                            else
                            {
                                Log.Error("Could not fetch temporary session key from os2faktor. Error: {0} '{1}'", response.StatusCode, response.ReasonPhrase);
                                return;
                            }
                        }
                        catch (System.Exception ex)
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
    }
}
