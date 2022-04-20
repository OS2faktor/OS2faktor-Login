using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using Microsoft.Win32;
using Serilog;
using System;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace ResetPasswordDialog
{
    public partial class ResetPasswordForm : Form
    {
        public  ResetPasswordForm()
        {
            Log.Verbose("Initialising components");
            InitializeComponent();
            Log.Verbose("Components initialised");
        }

        private void button1_Click(object sender, EventArgs e)
        {
            Log.Verbose("Close button clicked");
            Application.Exit();
        }

        private void webView_CoreWebView2InitializationCompleted(object sender, Microsoft.Web.WebView2.Core.CoreWebView2InitializationCompletedEventArgs e)
        {
            Log.Verbose("CoreWebView2Initialization called (Success: {0})", e.IsSuccess);
            if (!e.IsSuccess)
            {
                Exception initializationException = e.InitializationException;
                Log.Error(initializationException, initializationException.Message);
                Application.Exit();
                return;
            }
            try
            {
                Microsoft.Web.WebView2.Core.CoreWebView2Settings settings = webView.CoreWebView2.Settings;
                settings.AreBrowserAcceleratorKeysEnabled = false; // Disables f5, ctrl+p and so on
                settings.AreDefaultContextMenusEnabled = false; // Disables context menu (right clicking)
                settings.AreDefaultScriptDialogsEnabled = false;
                settings.AreDevToolsEnabled = false;
                settings.AreHostObjectsAllowed = false;
                settings.IsGeneralAutofillEnabled = false;
                settings.IsPasswordAutosaveEnabled = false;
                settings.IsPinchZoomEnabled = false;
                settings.IsStatusBarEnabled = false;
                settings.IsWebMessageEnabled = false;
                settings.IsZoomControlEnabled = false;

                settings.IsBuiltInErrorPageEnabled = true; // Not sure if needed for dev. This is needed to show cert error/unsafe pages error page
                settings.IsScriptEnabled = true; // Needed for nemid
                Log.Verbose("AcceleratorKeys and contextmenu disabled");


                webView.CoreWebView2.NewWindowRequested += CoreWebView2_NewWindowRequested;
                Log.Verbose("New window popups disabled");


                // Fetch base url from config and set source to change password web page
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
                        string baseUrl = null;
                        try
                        {
                            baseUrl = (string)settingsKey.GetValue("os2faktorBaseUrl");
                        }
                        catch (Exception)
                        {
                            Log.Error("No base url set in windows registry config, cannot open change password page");
                            Application.Exit();
                            return;
                        }

                        // Create URL from configured baseURL
                        string url = (baseUrl.EndsWith("/") ? baseUrl : (baseUrl + "/")) + "sso/saml/changepassword";
                        Log.Verbose("Calling url: " + url);
                        webView.Source = new Uri(url);
                    }
                }
            }
            catch (Exception ex)
            {
                Log.Error(ex, ex.Message);
                Application.Exit();
            }
        }

        private void CoreWebView2_NewWindowRequested(object sender, Microsoft.Web.WebView2.Core.CoreWebView2NewWindowRequestedEventArgs e)
        {
            Log.Verbose("CoreWebView2_NewWindowRequested called");
            e.Handled = true;
        }

        private void webView_NavigationCompleted(object sender, Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs e)
        {
            Log.Verbose("Website navigation completed");
        }

        private async void ResetPasswordForm_LoadAsync(object sender, EventArgs e)
        {
            Log.Verbose("ResetPasswordForm_LoadAsync calledx");
            try
            {
                _ = Application.StartupPath;
                CoreWebView2Environment env = await CoreWebView2Environment.CreateAsync(@"Microsoft.WebView2.FixedVersionRuntime.93.0.961.52.x64");
                await webView.EnsureCoreWebView2Async(env);
            }
            catch (Exception ex)
            {
                Log.Error(ex, "Failed creating and loading async CoreWebView2Environment");
                Application.Exit();
            }
        }

        private void ResetPasswordForm_Deactivate(object sender, EventArgs e)
        {
            Log.Verbose("ResetPasswordForm_Deactivate called");
            Application.Exit();
        }
    }
}
