using System.Collections.Generic;
using System.Data.Common;
using System.Data.SqlClient;

namespace OS2faktorADSync
{
    public class SQLService
    {
        public static Dictionary<string, string> GetCpr(string sAMAccountName)
        {
            if (!Settings.GetBooleanValue("SQL.Enabled"))
            {
                return null;
            }

            using (DbConnection connection = new SqlConnection(Settings.GetStringValue("SQL.ConnectionString")))
            {
                connection.Open();

                string sql = Settings.GetStringValue("SQL.SingleQuery").Replace("?", sAMAccountName);
                
                using (DbCommand command = new SqlCommand(sql, (SqlConnection)connection))
                {
                    using (DbDataReader reader = command.ExecuteReader())
                    {
                        if (reader.Read())
                        {
                            Dictionary<string, string> map = new Dictionary<string, string>();

                            string cpr = (string)reader["cpr"];
                            map.Add(sAMAccountName.ToLower(), cpr);

                            return map;
                        }
                    }
                }
            }

            return null;
        }

        public static Dictionary<string, string> GetSAMAccountNameToCprMap()
        {
            if (!Settings.GetBooleanValue("SQL.Enabled"))
            {
                return null;
            }

            Dictionary<string, string> map = new Dictionary<string, string>();

            using (DbConnection connection = new SqlConnection(Settings.GetStringValue("SQL.ConnectionString")))
            {
                connection.Open();

                string sql = Settings.GetStringValue("SQL.AllQuery");

                using (DbCommand command = new SqlCommand(sql, (SqlConnection)connection))
                {
                    using (DbDataReader reader = command.ExecuteReader())
                    {
                        while (reader.Read())
                        {
                            string cpr = (string)reader["cpr"];
                            string sAMAccountName = ((string)reader["sAMAccountName"]).ToLower();

                            map.Add(sAMAccountName, cpr);
                        }
                    }
                }
            }

            return map;
        }
    }
}
