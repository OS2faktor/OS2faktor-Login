using System.Collections.Generic;
using System.Configuration;

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
        public string AccountExpireProperty { get; set; }
        public string[] AllProperties { get; set; }
        public string[] GroupProperties { get; set; }
        public string[] KombitProperties { get; set; }
        public string UserAccountControlProperty { get; set; }
        public string MemberOfProperty { get; set; }
        public string DistinguishedNameProperty { get; set; }
        public string NameProperty { get; set; }
        public string RoleNameProperty { get; set; }
        public string RoleDomainProperty { get; set; }
        public string RoleCvrProperty { get; set; }
        public string DescriptionProperty { get; set; }
        public string PwdLastSetProperty { get; set; }
        public string MitIDUuidProperty { get; set; }
        public string EanProperty { get; set; }

        public PropertyResolver()
        {
            CprProperty = Settings.GetStringValue("ActiveDirectory.Property.Cpr");
            DistinguishedNameProperty = "DistinguishedName";
            ChosenNameProperty = "displayName";
            FirstnameProperty = "givenName";
            SurnameProperty = "sn";
            EmailProperty = "mail";
            SAMAccountNameProperty = "sAMAccountName";
            DeletedProperty = "isdeleted";
            ObjectGuidProperty = Settings.GetStringValue("ActiveDirectory.Property.Guid");
            AccountExpireProperty = "accountExpires";
            UserAccountControlProperty = "useraccountcontrol";
            MemberOfProperty = "MemberOf";
            NameProperty = "Name";
            RoleNameProperty = Settings.GetStringValue("Kombit.RoleNameAttribute");
            RoleDomainProperty = Settings.GetStringValue("Kombit.RoleDomainAttribute");
            RoleCvrProperty = Settings.GetStringValue("Kombit.RoleCvrAttribute");
            DescriptionProperty = "description";
            PwdLastSetProperty = "pwdlastset";
            MitIDUuidProperty = Settings.GetStringValue("ActiveDirectory.Property.MitIDErhvervUuid");
            EanProperty = Settings.GetStringValue("ActiveDirectory.Property.Ean");

            var allProperties = new List<string>();
            allProperties.AddRange(
                new string[]
                {
                    DistinguishedNameProperty,
                    ChosenNameProperty,
                    FirstnameProperty,
                    SurnameProperty,
                    EmailProperty,
                    SAMAccountNameProperty,
                    DeletedProperty,
                    AccountExpireProperty,
                    UserAccountControlProperty,
                    MemberOfProperty,
                    PwdLastSetProperty
                });

            // objectGuids
            if (!string.IsNullOrEmpty(ObjectGuidProperty) && !"objectGUID".Equals(ObjectGuidProperty))
            {
                allProperties.Add(ObjectGuidProperty);
                allProperties.Add("objectGUID");
            }
            else
            {
                allProperties.Add("objectGUID");
                ObjectGuidProperty = "objectGUID";
            }

            if (!string.IsNullOrEmpty(MitIDUuidProperty))
            {
                allProperties.Add(MitIDUuidProperty);
            }

            if (!string.IsNullOrEmpty(CprProperty))
            {
                allProperties.Add(CprProperty);
            }

            if (!string.IsNullOrEmpty(EanProperty))
            {
                allProperties.Add(EanProperty);
            }

            // Resolve attributes fields, Value is eqial to AD attributes that needs to be read.
            Dictionary<string, string> attributes = Settings.GetStringValues("ActiveDirectory.Attributes.");
            if (attributes != null)
            {
                foreach (var value in attributes.Values)
                {
                    if (value.Length > 0)
                    {
                        allProperties.Add(value);
                    }
                }
            }

            AllProperties = allProperties.ToArray();

            var groupProperties = new List<string>();
            groupProperties.AddRange(
                new string[]
                {
                    "objectGUID",
                    DistinguishedNameProperty,
                    NameProperty,
                    DescriptionProperty
                });

            GroupProperties = groupProperties.ToArray();

            var kombitProperties = new List<string>();
            kombitProperties.AddRange(
                new string[]
                {
                    DistinguishedNameProperty,
                    RoleNameProperty,
                    RoleDomainProperty,
                    RoleCvrProperty
                });

            KombitProperties = kombitProperties.ToArray();
        }
    }
}