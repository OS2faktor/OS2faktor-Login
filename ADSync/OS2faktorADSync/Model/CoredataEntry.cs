using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Linq;

namespace OS2faktorADSync
{
    public class CoredataEntry
    {
        [JsonProperty(PropertyName = "uuid")]
        public string Uuid { get; set; }

        private string _cpr;
        [JsonProperty(PropertyName = "cpr")]
        public string Cpr {
            get
            {
                return _cpr;
            }
            set {
                this._cpr = null;
                
                if (value != null && value.Length >= 10)
                {
                    string tmp = value;

                    if (tmp.Length > 10)
                    {
                        tmp = tmp.Replace("-", "");
                    }

                    if (Settings.GetBooleanValue("ActiveDirectory.Property.Cpr.Encoded"))
                    {
                        tmp = DecodeCpr(tmp);
                    }

                    if (tmp.Length == 10)
                    {
                        this._cpr = tmp;
                    }
                }
            }
        }

        [JsonProperty(PropertyName = "name")]
        public string Name { get; set; }

        [JsonProperty(PropertyName = "email")]
        public string Email { get; set; }

        [JsonProperty(PropertyName = "samAccountName")]
        public string SamAccountName { get; set; }

        [JsonProperty(PropertyName = "subDomain")]
        public string SubDomain { get; set; }

        [JsonProperty(PropertyName = "nsisAllowed")]
        public bool NSISAllowed { get; set; }

        [JsonProperty(PropertyName = "attributes")]
        public Dictionary<string, string> Attributes { get; set; }

        [JsonProperty(PropertyName = "expireTimestamp")]
        public string ExpireTimestamp { get; set; }

        [JsonProperty(PropertyName = "nextPasswordChange")]
        public string NextPasswordChange { get; set; }

        [JsonProperty(PropertyName = "transferToNemlogin")]
        public bool TransferToNemlogin { get; set; }

        [JsonProperty(PropertyName = "department")]
        public string Department { get; set; }

        [JsonProperty(PropertyName = "ean")]
        public string Ean { get; set; }

        [JsonIgnore]
        public bool Deleted { get; set; }

        [JsonIgnore]
        public bool Disabled { get; set; }

        public bool IsActive()
        {
            return !Deleted && !Disabled;
        }

        public bool IsValid()
        {
            return !string.IsNullOrEmpty(Cpr) && !string.IsNullOrEmpty(Name) && Guid.TryParse(Uuid, out _ );
        }

        private string DecodeCpr(string cpr)
        {
            if (long.TryParse(cpr, out long val))
            {
                val /= 33;
                val--;

                cpr = val.ToString();
                if (cpr.Length == 9)
                {
                    cpr = "0" + cpr;
                }
            }

            return cpr;
        }
    }
}
