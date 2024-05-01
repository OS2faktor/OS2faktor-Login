using Newtonsoft.Json;
using OS2faktorADSync.Model;
using Serilog;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;

namespace OS2faktorADSync
{
    public class BackendService
    {
        private const string version = "2.5.0";

        private readonly string baseUrl = Settings.GetStringValue("Backend.URL.Base");
        public ILogger Logger { get; set; }

        public void FullSync(IEnumerable<CoredataEntry> users)
        {
            Coredata coredata = new Coredata()
            {
                Domain = Settings.GetStringValue("Backend.Domain"),
                EntryList = users.ToList()
            };

            // Fill out SubDomain if configured
            string subDomain = Settings.GetStringValue("Backend.SubDomain");
            if (!string.IsNullOrEmpty(subDomain))
            {
                coredata.GlobalSubDomain = subDomain;
            }

            string json = JsonConvert.SerializeObject(coredata);
            Logger.Verbose("Invoking backend full sync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "full", json);
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call fullsync backend");

                    throw ex;
                }
            }
        }

        internal List<CoredataMitIDStatus> GetMitIDStatus()
        {
            Logger.Verbose("Invoking backend mitid status request");

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    string domain = Settings.GetStringValue("Backend.Domain");
                    string result = webClient.DownloadString(baseUrl + "nemloginStatus?domain=" + domain);

                    return JsonConvert.DeserializeObject<CoredataMitIDStatusResponse>(result).Entries;
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call mitid status backend");

                    throw ex;
                }
            }
        }

        public void NSISSync(CoredataNSISAllowed nsisData)
        {
            if (nsisData == null || !(nsisData.NSISAllowed.Count() > 0))
            {
                Logger.Warning("No NSIS Users found");
                return;
            }
            nsisData.Domain = Settings.GetStringValue("Backend.Domain");

            var json = JsonConvert.SerializeObject(nsisData);
            Logger.Verbose("Invoking backend groups sync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "nsisallowed/load", json);
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call groups backend");

                    throw ex;
                }
            }
        }

        public void NSISTransferToNemLoginSync(CoredataTransferToNemLogin nsisData)
        {
            if (nsisData == null || !(nsisData.TransferToNemLogin.Count() > 0))
            {
                Logger.Warning("No NemLog-in Users found");
                return;
            }
            nsisData.Domain = Settings.GetStringValue("Backend.Domain");

            var json = JsonConvert.SerializeObject(nsisData);
            Logger.Verbose("Invoking backend groups sync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "transfertonemlogin/load", json);
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call groups backend");

                    throw ex;
                }
            }
        }

        public void FullLoadKombitJfr(CoreDataFullJfr body)
        {
            string json = JsonConvert.SerializeObject(body);
            Logger.Verbose("Invoking backend JobFunctionRoles full sync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "jfr/full", json);
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call Kombit JFR fullsync backend");

                    throw ex;
                }
            }
        }

        public void FullLoadKombitAttributes(CoreDataKombitAttributes body)
        {
            string json = JsonConvert.SerializeObject(body);
            Logger.Verbose("Invoking backend kombit attributes fullsync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "kombitAttributes/load/full", json);
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call Kombit attributes fullsync backend");

                    throw ex;
                }
            }
        }

        public void DeltaDeleteSync(IEnumerable<CoredataDeleteEntry> users)
        {
            if (users.Count() > 0)
            {
                CoredataDelete coredata = new CoredataDelete()
                {
                    Domain = Settings.GetStringValue("Backend.Domain"),
                    EntryList = users.ToList()
                };

                var json = JsonConvert.SerializeObject(coredata);
                Logger.Verbose("Invoking backend delete delta sync: {0}", json);

                using (WebClient webClient = new WebClient())
                {
                    webClient.Headers["Content-Type"] = "application/json";
                    webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                    webClient.Headers["ClientVersion"] = version;
                    webClient.Encoding = System.Text.Encoding.UTF8;

                    try
                    {
                        // strip slash
                        string url = baseUrl.Substring(0, baseUrl.Length - 1);
                        webClient.UploadString(url, "DELETE", json);
                    }
                    catch (WebException ex)
                    {
                        LogException(ex, "Failed to call delete delta backend");

                        throw ex;
                    }
                }
            }
        }

        public void DeltaSync(IEnumerable<CoredataEntry> users)
        {
            if (users.Count() > 0)
            {
                Coredata coredata = new Coredata()
                {
                    Domain = Settings.GetStringValue("Backend.Domain"),
                    EntryList = users.ToList()
                };

                var json = JsonConvert.SerializeObject(coredata);
                Logger.Verbose("Invoking backend delta sync: {0}", json);

                using (WebClient webClient = new WebClient())
                {
                    webClient.Headers["Content-Type"] = "application/json";
                    webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                    webClient.Headers["ClientVersion"] = version;
                    webClient.Encoding = System.Text.Encoding.UTF8;

                    try
                    {
                        webClient.UploadString(baseUrl + "delta", json);
                    }
                    catch (WebException ex)
                    {
                        LogException(ex, "Failed to call delta backend");

                        throw ex;
                    }
                }
            }
        }


        public void GroupSync(CoredataGroup groupData)
        {
            if (groupData == null || !(groupData.Groups.Count() > 0))
            {
                Logger.Warning("No Groupdata found");
                return;
            }

            groupData.Domain = Settings.GetStringValue("Backend.Domain");

            var json = JsonConvert.SerializeObject(groupData);
            Logger.Verbose("Invoking backend groups sync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Headers["ClientVersion"] = version;
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "groups/load/full", json);
                }
                catch (WebException ex)
                {
                    LogException(ex, "Failed to call groups backend");

                    throw ex;
                }
            }
        }

        private void LogException(WebException ex, string msg)
        {
            string body = "<null>";
            try
            {
                body = new StreamReader(ex.Response.GetResponseStream()).ReadToEnd();
            }
            catch (Exception)
            {
                ; // ignore
            }

            WebExceptionStatus status = ex.Status;

            Logger.Error(ex, msg + " : " + status + " : " + body);
        }
    }
}