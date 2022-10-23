using Newtonsoft.Json;
using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class CoredataTransferToNemLogin
    {
        [JsonProperty(PropertyName = "domain")]
        public string Domain { get; set; }

        [JsonProperty(PropertyName = "nemLoginUserUuids")]
        public List<string> TransferToNemLogin { get; set; }

        public CoredataTransferToNemLogin()
        {
            this.TransferToNemLogin = new List<string>();
        }
    }
}