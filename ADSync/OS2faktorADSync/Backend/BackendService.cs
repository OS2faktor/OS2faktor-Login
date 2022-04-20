using Newtonsoft.Json;
using OS2faktorADSync.Model;
using Serilog;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;

namespace OS2faktorADSync
{
    public class BackendService
    {
        private const string version = "1.5.1";

        private readonly string baseUrl = Settings.GetStringValue("Backend.URL.Base");
        public ILogger Logger { get; set; }

        public void FullSync(IEnumerable<CoredataEntry> users)
        {
            Coredata coredata = new Coredata()
            {
                Domain = Settings.GetStringValue("Backend.Domain"),
                EntryList = users.ToList()
            };

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
                    WebExceptionStatus status = ex.Status;
                    Logger.Error(ex, "Failed to call fullsync backend: " + status);

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
                    WebExceptionStatus status = ex.Status;
                    Logger.Error(ex, "Failed to call groups backend: " + status);

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
                    WebExceptionStatus status = ex.Status;
                    Logger.Error(ex, "Failed to call Kombit JFR fullsync backend: " + status);

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
                        WebExceptionStatus status = ex.Status;
                        Logger.Error(ex, "Failed to call delete delta backend: " + status);

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
                        WebExceptionStatus status = ex.Status;
                        Logger.Error(ex, "Failed to call delta backend: " + status);

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
                    WebExceptionStatus status = ex.Status;
                    Logger.Error(ex, "Failed to call groups backend: " + status);

                    throw ex;
                }
            }
        }
    }
}