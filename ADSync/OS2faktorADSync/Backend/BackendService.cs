using Newtonsoft.Json;
using Serilog;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;

namespace OS2faktorADSync
{
    public class BackendService
    {
        private readonly string baseUrl = Settings.GetStringValue("Backend.URL.Base");
        public ILogger Logger { get; set; }

        public void FullSync(IEnumerable<CoredataEntry> users)
        {
            Coredata coredata = new Coredata()
            {
                Domain = Settings.GetStringValue("Backend.Domain"),
                EntryList = users.ToList()
            };

            var json = JsonConvert.SerializeObject(coredata);
            Logger.Verbose("Invoking backend full sync: {0}", json);

            using (WebClient webClient = new WebClient())
            {
                webClient.Headers["Content-Type"] = "application/json";
                webClient.Headers["ApiKey"] = Settings.GetStringValue("Backend.Password");
                webClient.Encoding = System.Text.Encoding.UTF8;

                try
                {
                    webClient.UploadString(baseUrl + "full", json);
                }
                catch (WebException ex)
                {
                    WebExceptionStatus status = ex.Status;
                    string resp = new StreamReader(ex.Response.GetResponseStream()).ReadToEnd();

                    Logger.Error("Failed to perform full sync (" + status + "). Response from backend: " + resp);
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
                        string resp = new StreamReader(ex.Response.GetResponseStream()).ReadToEnd();

                        Logger.Error("Failed to perform delta delete sync (" + status + "). Response from backend: " + resp);
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
                    webClient.Encoding = System.Text.Encoding.UTF8;

                    try
                    {
                        webClient.UploadString(baseUrl + "delta", json);
                    }
                    catch (WebException ex)
                    {
                        WebExceptionStatus status = ex.Status;
                        string resp = new StreamReader(ex.Response.GetResponseStream()).ReadToEnd();

                        Logger.Error("Failed to perform delta sync (" + status + "). Response from backend: " + resp);
                        throw ex;
                    }
                }
            }
        }
    }
}