using System.Collections.Generic;

namespace OS2faktorADSync
{
    public class PropertyResolver
    {
        public string CprProperty { get; set; }
        public string ChosenNameProperty { get; set; }
        public string FirstnameProperty { get; set; }
        public string SurnameProperty { get; set; }
        public string EmailProperty { get; set; }
        public string SAMAccountNameProperty { get; set; }
        public string DeletedProperty { get; set; }
        public string ObjectGuidProperty { get; set; }
        public string[] AllProperties { get; set; }
        public string UserAccountControlProperty { get; set; }
        public string MemberOfProperty { get; set; }


        public PropertyResolver()
        {
            CprProperty = Settings.GetStringValue("ActiveDirectory.Property.Cpr");
            ChosenNameProperty = "displayName";
            FirstnameProperty = "givenName";
            SurnameProperty = "sn";
            EmailProperty = "mail";
            SAMAccountNameProperty = "sAMAccountName";
            DeletedProperty = "isdeleted";
            ObjectGuidProperty = "objectGUID";
            UserAccountControlProperty = "useraccountcontrol";
            MemberOfProperty = "memberOf";

            var allProperties = new List<string>();
            allProperties.AddRange(
                new string[]
                {
                    CprProperty,
                    ChosenNameProperty,
                    FirstnameProperty,
                    SurnameProperty,
                    EmailProperty,
                    SAMAccountNameProperty,
                    DeletedProperty,
                    ObjectGuidProperty,
                    UserAccountControlProperty,
                    MemberOfProperty
                });

            AllProperties = allProperties.ToArray();
        }
    }
}