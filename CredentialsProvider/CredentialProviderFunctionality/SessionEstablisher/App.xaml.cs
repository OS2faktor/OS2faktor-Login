using Microsoft.Win32;
using Serilog;
using System;
using System.Buffers.Text;
using System.Diagnostics;
using System.IO;
using System.Security.Principal;
using System.Threading;
using System.Windows;

namespace SessionEstablisher
{
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application
    {
        private static DateTime lastModifiedTime = DateTime.MinValue;

        private void Application_Startup(object sender, StartupEventArgs e)
        {
            try
            {
                SetupLogging();

                try
                {
                    Log.Verbose($"Running as: {WindowsIdentity.GetCurrent().Name}");

                    this.ShutdownMode = ShutdownMode.OnExplicitShutdown; //this prevents application form closing when we close a window.
                    Log.Verbose("Shutdown mode set");

                    WaitInCaseOfRoamingBrowserProfile();

                    // Check that we were given a session establishing token via commandline argument
                    if (e.Args == null || e.Args.Length != 1)
                    {
                        Log.Error("Arguments passed to SessionEstablisher was not correct");
                        return;
                    }

                    string token = e.Args[0];
                    if (token != null)
                    {
                        Log.Information("New token ready for establishing session");

                        bool chromeEnabled = true;
                        bool edgeEnabled = true;
                        bool firefoxEnabled = false;
                        UpdateDefaultSettings(ref chromeEnabled, ref edgeEnabled, ref firefoxEnabled);

                        if (chromeEnabled)
                        {
                            Log.Information("Establishing session in chrome");
                            string chromePath = fetchExePath(@"HKEY_CLASSES_ROOT\ChromeHTML\shell\open\command", "chrome.exe", "\"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\"");
                            if (chromePath != null)
                            {
                                runBrowserWithToken(chromePath, token);
                            }
                        }

                        if (edgeEnabled)
                        {
                            Log.Information("Establishing session in edge");
                            string edgePath = fetchExePath(@"HKEY_CLASSES_ROOT\MSEdgeHTM\shell\open\command", "msedge.exe", "\"C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe\"");
                            if (edgePath != null)
                            {
                                runBrowserWithToken(edgePath, token);
                            }
                        }

                        if (firefoxEnabled)
                        {
                            Log.Information("Establishing session in firefox");
                            string firefoxPath = fetchExePath(@"HKEY_CLASSES_ROOT\firefox\shell\open\command", "firefox.exe", "\"C:\\Program Files\\Mozilla Firefox\\firefox.exe\"");
                            if (firefoxPath != null)
                            {
                                runBrowserWithToken(firefoxPath, token);
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    Log.Error("Error running job", ex);
                }
            }
            catch (Exception)
            {
                ;
            }

            Shutdown();
        }

        private static void UpdateDefaultSettings(ref bool chromeEnabled, ref bool edgeEnabled, ref bool firefoxEnabled)
        {
            try
            {
                using (RegistryKey rootKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
                {
                    if (rootKey == null)
                    {
                        return;
                    }

                    using (RegistryKey settingsKey = rootKey.OpenSubKey(@"SOFTWARE\DigitalIdentity\OS2faktorLogin", false))
                    {
                        if (settingsKey == null || !(settingsKey.GetValueNames().Length > 0))
                        {
                            return;
                        }

                        object logPathObj = settingsKey.GetValue("EstablishSessionChrome");
                        if (logPathObj != null)
                        {
                            chromeEnabled = (int)logPathObj == 1;
                        }

                        logPathObj = settingsKey.GetValue("EstablishSessionEdge");
                        if (logPathObj != null)
                        {
                            edgeEnabled = (int)logPathObj == 1;
                        }

                        logPathObj = settingsKey.GetValue("EstablishSessionFirefox");
                        if (logPathObj != null)
                        {
                            firefoxEnabled = (int)logPathObj == 1;
                        }
                    }
                }
            }
            catch (Exception)
            {
                Log.Error("Could not fetch Session establisher settings from registry");
                return;
            }
        }

        private static void WaitInCaseOfRoamingBrowserProfile()
        {
            string settingsPath = @"SOFTWARE\DigitalIdentity\OS2faktorLogin";
            using (RegistryKey rootKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
            {
                if (rootKey == null)
                {
                    Log.Verbose(@"Could not open HKEY_LOCAL_MACHINE. Skipping wait for disk access logic");
                    return;
                }

                using (RegistryKey settingsKey = rootKey.OpenSubKey(settingsPath, false))
                {
                    if (settingsKey == null || !(settingsKey.GetValueNames().Length > 0))
                    {
                        Log.Verbose(@"Could not open HKEY_LOCAL_MACHINE\SOFTWARE\DigitalIdentity\OS2faktorLogin. Skipping wait for disk access logic");
                        return;
                    }

                    // Settings avaliable, continue process
                    string WaitForDiskAccess = null;

                    try
                    {
                        object WaitForDiskAccessObj = settingsKey.GetValue("WaitForDiskAccess");
                        if (WaitForDiskAccessObj != null)
                        {
                            WaitForDiskAccess = (string)WaitForDiskAccessObj;
                            if (WaitForDiskAccess.Length > 0)
                            {
                                Log.Information($"WaitForDiskAccess: {WaitForDiskAccess}");
                                // In case of roaming browser profiles we need to wait until they are available otherwise we will establish a session in the wrong profile
                                int breaker = 10;
                                while (breaker > 0 && !Directory.Exists(WaitForDiskAccess))
                                {
                                    if (breaker == 10)
                                    {
                                        Log.Information("Entered Directory Exists waiter");
                                    }

                                    breaker--;
                                    Thread.Sleep(1000);
                                }

                                if (breaker != 10)
                                {
                                    Log.Information($"Exited Directory Exists waiter. Breaker: {breaker}, Directory.Exists(): {Directory.Exists(WaitForDiskAccess)}");
                                }
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        Log.Error("Error in WaitForDiskAccess", ex);
                        return;
                    }
                }
            }
        }

        private static void SetupLogging()
        {
            try
            {
                // Open up the settings, we need these to run the program so without them it just closes
                string settingsPath = @"SOFTWARE\DigitalIdentity\OS2faktorLogin";

                using (RegistryKey rootKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
                {
                    if (rootKey == null)
                    {
                        return;
                    }

                    using (RegistryKey settingsKey = rootKey.OpenSubKey(settingsPath, false))
                    {
                        if (settingsKey == null || !(settingsKey.GetValueNames().Length > 0))
                        {
                            return;
                        }

                        // Settings avaliable, continue process
                        string logPath = null;

                        try
                        {
                            object logPathObj = settingsKey.GetValue("SessionEstablisherLogPath");
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

                        Log.Information("Logging initialized, SessionEstablisher invoked.");
                    }
                }
            }
            catch (Exception)
            {
                ;
            }
        }

        private static void runBrowserWithToken(string exePath, string token)
        {
            // Process to run
            ProcessStartInfo startInfo = new ProcessStartInfo();
            startInfo.FileName = exePath;

            startInfo.Arguments = $" \"{token}\"";

            // Settings
            startInfo.UseShellExecute = true;
            startInfo.CreateNoWindow = true;
            //startInfo.WindowStyle = ProcessWindowStyle.Minimized;

            using (Process exeProcess = Process.Start(startInfo))
            {
                if (exeProcess != null)
                {
                    Log.Verbose($"Process started with PID: {exeProcess.Id}");
                }
            }
        }

        private static string fetchExePath(string registryPath, string exeName, string defaultPath)
        {
            string exeValue = defaultPath;
            try
            {
                exeValue = Registry.GetValue(registryPath, null, null) as string;
                if (exeValue == null)
                {
                    Log.Warning("No path to try, trying default path");
                    exeValue = defaultPath; // Default path to try
                }
                Log.Verbose($"Trying exePath: {exeValue}");
            }
            catch (Exception)
            {
                exeValue = defaultPath;
            }


            var split = exeValue.Split('\"');
            exeValue = split.Length >= 2 ? split[1] : null;
            if (exeValue == null || !exeValue.EndsWith(exeName) || !File.Exists(exeValue))
            {
                return null;
            }
            Log.Verbose($"Cleaned up exePath: {exeValue}");
            return exeValue;
        }
    }
}
