using Microsoft.Win32;
using Serilog;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace ResetPasswordDialog
{
    static class Program
    {
        /// <summary>
        /// The main entry point for the application.
        /// </summary>
        [STAThread]
        static void Main()
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
                        try
                        {
                            object logPathObj = settingsKey.GetValue("ResetPasswordLogPath");
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

                        Log.Information("Logging initialized, ResetPasswordDialog invoked.");

                        // Trying to delete previous userDataFolder
                        string currentDir = Application.StartupPath;
                        Log.Verbose("Current dir: '{0}'", currentDir);
                        if (!currentDir.EndsWith("\\") && !currentDir.EndsWith("/"))
                        {
                            currentDir += "\\";
                        }

                        string userDataFolder = currentDir + "ResetPasswordDialog.exe.WebView2\\EBWebView\\Default";
                        Log.Verbose("Trying to delete UserData folder: {0}", userDataFolder);
                        if (Directory.Exists(userDataFolder))
                        {
                            Log.Verbose("UserData folder exists");
                            try
                            {
                                Directory.Delete(userDataFolder, true);
                                Log.Verbose("Deleted UserData folder");
                            }
                            catch (Exception ex)
                            {
                                Log.Error(ex, ex.Message);
                                return;
                            }
                        }

                        // Starting up reset password dialog
                        Log.Verbose("Starting WinForms");
                        Application.EnableVisualStyles();
                        Application.SetCompatibleTextRenderingDefault(false);
                        Application.Run(new ResetPasswordForm());
                    }
                }
            }
            catch (Exception ex)
            {
                try
                {
                    Log.Error(ex, "Failed to run ResetPassword command");
                }
                catch (Exception)
                {
                    ;
                }
            }
        }
    }
}
