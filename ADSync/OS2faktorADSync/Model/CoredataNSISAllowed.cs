using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class CoredataNSISAllowed
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "nsisUserUuids")]
        public List<string> NSISAllowed { get; set; }

        public CoredataNSISAllowed()
        {
            this.NSISAllowed = new List<string>();
        }
    }
}