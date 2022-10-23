using OS2faktorADSync.Model;
using Quartz;
using Serilog;
using System.Collections.Generic;
using System.Data.SqlClient;
using System.Threading.Tasks;

namespace OS2faktorADSync
{
    [DisallowConcurrentExecution]
    public class KombitAttributesSyncJob : IJob
    {
        public ILogger Logger { get; set; }
        public BackendService BackendService { get; set; }

        public Task Execute(IJobExecutionContext context)
        {
            Logger.Information("Performing full KOMBIT attributes sync");

            try
            {
                var entries = new List<CoreDataKombitAttributeEntry>();

                using (SqlConnection connection = new SqlConnection(Settings.GetStringValue("Kombit.Attributes.ConnectionString")))
                {
                    connection.Open();
                    using (SqlCommand command = new SqlCommand(Settings.GetStringValue("Kombit.Attributes.SqlQuery"), connection))
                    {
                        using (SqlDataReader reader = command.ExecuteReader())
                        {
                            while (reader.Read())
                            {
                                string sAMAccountName = (string)reader["sAMAccountName"];
                                string attributeKey = (string)reader["attributeKey"];
                                string attributeValue = (string)reader["attributeValue"];

                                bool found = false;
                                foreach (var entry in entries)
                                {
                                    if (string.Equals(entry.SamAccountName, sAMAccountName))
                                    {
                                        entry.KombitAttributes.Add(attributeKey, attributeValue);
                                        found = true;
                                        break;
                                    }
                                }

                                if (!found)
                                {
                                    var entry = new CoreDataKombitAttributeEntry();
                                    entry.SamAccountName = sAMAccountName;
                                    entry.KombitAttributes = new Dictionary<string, string>();
                                    entry.KombitAttributes.Add(attributeKey, attributeValue);

                                    entries.Add(entry);
                                }
                            }
                        }
                    }
                }

                Logger.Information($"Found {entries?.Count} users with KOMBIT attribute assignments");

                CoreDataKombitAttributes body = new CoreDataKombitAttributes();
                body.Domain = Settings.GetStringValue("Backend.Domain");
                body.EntryList = entries;

                BackendService.FullLoadKombitAttributes(body);
            }
            catch (System.Exception e)
            {
                Logger.Error(e, "Exception caught in SynchronizeJob (full kombit attributes)");
            }

            Logger.Information("Full sync of KOMBIT attributes complete");

            return Task.CompletedTask;
        }
    }
}