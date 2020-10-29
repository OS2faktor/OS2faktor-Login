using System;
using System.Security.Cryptography;

namespace OS2faktor_Password_Agent
{
    class HMacUtil
    {
        public static string Encode(string message)
        {
            byte[] rawKey = System.Text.Encoding.UTF8.GetBytes(Settings.GetStringValue("webSocketKey"));
            byte[] rawMessage = System.Text.Encoding.UTF8.GetBytes(message);

            using (HMACSHA256 hmac = new HMACSHA256(rawKey))
            {
                byte[] result = hmac.ComputeHash(rawMessage);
                return Convert.ToBase64String(result);
            }
        }
    }
}
