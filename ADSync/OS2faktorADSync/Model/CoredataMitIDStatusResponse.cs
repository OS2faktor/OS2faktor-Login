using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class CoredataMitIDStatusResponse
    {
        [JsonProperty(PropertyName = "entries")]
        public List<CoredataMitIDStatus> Entries { get; set; }

    }
}