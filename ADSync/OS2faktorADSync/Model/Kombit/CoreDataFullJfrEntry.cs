using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync.Model
{
    public class CoreDataFullJfrEntry
    {
        [JsonProperty(PropertyName = "samAccountName")]
        public string SamAccountName { get; set; }

        [JsonProperty(PropertyName = "uuid")]
        public string Uuid { get; set; }

        [JsonProperty(PropertyName = "jfrs")]
        public List<Jfr> JobFunctionRoles { get; set; }

    }
}