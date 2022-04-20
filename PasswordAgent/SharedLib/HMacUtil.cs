using System;
using System.Security.Cryptography;

namespace OS2faktor
{
    public class HMacUtil
    {
        private Settings settings;

        public HMacUtil(Settings settings)
        {
            this.settings = settings;
        }

        public string Encode(string message)
        {
            byte[] rawKey = System.Text.Encoding.UTF8.GetBytes(settings.GetStringValue("webSocketKey"));
            byte[] rawMessage = System.Text.Encoding.UTF8.GetBytes(message);

            using (HMACSHA256 hmac = new HMACSHA256(rawKey))
            {
                byte[] result = hmac.ComputeHash(rawMessage);
                return Convert.ToBase64String(result);
            }
        }
    }
}
