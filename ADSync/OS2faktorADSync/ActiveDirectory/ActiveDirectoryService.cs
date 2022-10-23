using OS2faktorADSync.Model;
using Serilog;
using System;
using System.Collections;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.ActiveDirectory;
using System.Linq;
using System.Runtime.InteropServices;
using System.Security;

namespace OS2faktorADSync
{
    public class ActiveDirectoryService
    {
        const int AccountDisable = 2;

        public ILogger Logger { get; set; }
        private readonly PropertyResolver propertyResolver = new PropertyResolver();
        private readonly string nsisAllowedGroup;
        private readonly string transferToNemloginGroup;
        private readonly string rolesRoot;
        private readonly string rootMembershipGroup;
        private readonly Boolean groupsInGroups;
        private readonly string defaultCvr;
        private readonly string defaultDomain;
        private readonly string roleNamePrefix;

        public ActiveDirectoryService()
        {
            nsisAllowedGroup = Settings.GetStringValue("ActiveDirectory.NSISAllowed.Group");
            transferToNemloginGroup = Settings.GetStringValue("ActiveDirectory.TransferToNemlogin.Group");
            rootMembershipGroup = Settings.GetStringValue("ActiveDirectory.Group.Root");
            rolesRoot = Settings.GetStringValue("Kombit.RoleOU");
            defaultDomain = Settings.GetStringValue("Kombit.RoleDomainDefault");
            defaultCvr = Settings.GetStringValue("Kombit.RoleCvrDefault");
            groupsInGroups = Settings.GetBooleanValue("Kombit.GroupsInGroups");
            roleNamePrefix = Settings.GetStringValue("Kombit.RoleNameAttributePrefix");
        }

        public IEnumerable<CoredataEntry> GetFullSyncUsers(out byte[] directorySynchronizationCookie)
        {
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                Dictionary<string, CoredataEntry> result = new Dictionary<string, CoredataEntry>();
                string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*");

                Logger.Debug("Performing search with filter: " + filter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None);
                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        Logger.Information("Found {0} users in Active Directory", searchResultCollection.Count);

                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            CoredataEntry user = CreateCoredataEntryFromAD(searchResult.Properties);
                            if (user.IsActive() && user.IsValid())
                            {
                                result.Add(user.Uuid, user);
                            }
                        }

                        Logger.Information("{0} users where active and valid", result.Count);
                    }

                    directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
                }

                if (!string.IsNullOrEmpty(nsisAllowedGroup))
                {
                    Logger.Debug("Performing lookup of membership in NSIS group");

                    // Additional search for members of a specific group (Recursive / transative)
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);

                    Logger.Debug("Performing search with filter: " + filter);

                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Debug("Found {0} users in NSIS group", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                if (result.ContainsKey(Guid))
                                {
                                    result[Guid].NSISAllowed = true;
                                }
                                else
                                {
                                    Logger.Debug("Could not match user with ObjectGuid " + Guid + " to any user previously found");
                                }
                            }
                        }
                    }
                }

                if (!string.IsNullOrEmpty(transferToNemloginGroup))
                {
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + transferToNemloginGroup);

                    Logger.Debug("Performing search with filter: " + filter);

                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Debug("Found {0} users in transfer to nemlogin group", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                if (result.ContainsKey(Guid))
                                {
                                    result[Guid].TransferToNemlogin = true;
                                }
                                else
                                {
                                    Logger.Debug("Could not match user with ObjectGuid " + Guid + " to any user previously found");
                                }
                            }
                        }
                    }
                }

                return result.Values;
            }
        }

        public DeltaSync GetDeltaSyncUsers(ref byte[] directorySynchronizationCookie)
        {
            DeltaSync result = new DeltaSync();
            result.CreateEntries = new List<CoredataEntry>();
            result.DeleteEntries = new List<CoredataDeleteEntry>();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*");

                Logger.Debug("Performing search with filter: " + filter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None, directorySynchronizationCookie);

                    using (var searchResults = directorySearcher.FindAll())
                    {
                        foreach (SearchResult searchResult in searchResults)
                        {
                            Logger.Verbose("Delta sync searchResult: {@searchResult}", searchResult);

                            if (searchResult.Properties.GetValue<Boolean>(propertyResolver.DeletedProperty, false))
                            {
                                Logger.Information("Received delta sync deleted object {0}", searchResult.Path);
                            }
                            else if (searchResult.Path.Contains("CN=Deleted Objects"))
                            {
                                Logger.Information("Object purged from AD Deleted Objects. Ignoring. {0}", searchResult.Path);
                            }
                            else
                            {
                                Logger.Information("Received delta sync user {0}", searchResult.Path);
                                //get properties from the directoryEntry because the delta search result only contain the changed properties

                                var coreDataEntry = CreateCoredataEntryFromAD(searchResult.GetDirectoryEntry().Properties);
                                if (coreDataEntry.IsValid())
                                {
                                    if (coreDataEntry.IsActive())
                                    {
                                        result.CreateEntries.Add(coreDataEntry);
                                    }
                                    else
                                    {
                                        result.DeleteEntries.Add(new CoredataDeleteEntry(coreDataEntry));
                                    }
                                }
                            }
                        }
                    }

                    directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
                }

                foreach (CoredataEntry entry in result.CreateEntries)
                {
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", propertyResolver.SAMAccountNameProperty + "=" + entry.SamAccountName, "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            if (searchResultCollection.Count == 1)
                            {
                                Logger.Debug("{0} is in NSIS allowed group", entry.SamAccountName);
                                entry.NSISAllowed = true;
                            }
                            else
                            {
                                Logger.Debug("{0} is NOT in NSIS allowed group", entry.SamAccountName);
                            }
                        }
                    }

                    if (!string.IsNullOrEmpty(transferToNemloginGroup))
                    {
                        filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", propertyResolver.SAMAccountNameProperty + "=" + entry.SamAccountName, "memberOf:1.2.840.113556.1.4.1941:=" + transferToNemloginGroup);
                        using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                        {
                            using (var searchResultCollection = directorySearcher.FindAll())
                            {
                                if (searchResultCollection.Count == 1)
                                {
                                    Logger.Debug("{0} is in transfer to nemlogin group", entry.SamAccountName);
                                    entry.TransferToNemlogin = true;
                                }
                                else
                                {
                                    Logger.Debug("{0} is NOT in transfer to nemlogin group", entry.SamAccountName);
                                }
                            }
                        }
                    }
                }

                return result;
            }
        }

        // TODO: burde være muligt at lave en dirSyncCookie på dette
        public CoredataNSISAllowed NSISAllowedSync()
        {
            CoredataNSISAllowed coredataNSISAllowed = new CoredataNSISAllowed();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // Additional search for members of a specific group (Recursive / transative)
                string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                            coredataNSISAllowed.NSISAllowed.Add(Guid);
                        }
                    }
                }
            }

            Logger.Information("Found {0} nsis users in NSISAllowedGroup", coredataNSISAllowed.NSISAllowed.Count());
            
            return coredataNSISAllowed;
        }

        // TODO: burde være muligt at lave en dirSyncCookie på dette
        public CoredataTransferToNemLogin TransferToNemloginSync()
        {
            CoredataTransferToNemLogin coredataTransferToNemlogin = new CoredataTransferToNemLogin();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // Additional search for members of a specific group (Recursive / transative)
                string filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + transferToNemloginGroup);
                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                            coredataTransferToNemlogin.TransferToNemLogin.Add(Guid);
                        }
                    }
                }
            }

            Logger.Information("Found {0} nsis users in transfer to nemlogin Group", coredataTransferToNemlogin.TransferToNemLogin.Count());

            return coredataTransferToNemlogin;
        }

        // TODO: også en DirSyncCookie her
        public CoredataGroup GroupSync()
        {
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // Populate groups property of coredata to syncronize groups in ad with groups in os2faktor login
                if (rootMembershipGroup != null && rootMembershipGroup.Length > 0)
                {
                    // Find group of members os2faktor "cares about"
                    string filter = CreateGroupFilter("!(isDeleted=TRUE)", "memberOf=" + rootMembershipGroup);

                    List<Group> groups = new List<Group>();
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.GroupProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} groups to send to OS2faktor", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                // Group GUID
                                string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                string GroupDN = searchResult.Properties.GetValue<string>(propertyResolver.DistinguishedNameProperty, null);
                                string GroupName = searchResult.Properties.GetValue<string>(propertyResolver.NameProperty, null);
                                string groupDescription = searchResult.Properties.GetValue<string>(propertyResolver.DescriptionProperty, null);
                                groups.Add(new Group(GroupDN, GroupName, groupDescription, Guid));
                            }
                        }
                    }

                    // Find all members of those groups and set them as a property on coredata entries
                    CoredataGroup coredataGroup = new CoredataGroup();
                    foreach (Group group in groups)
                    {
                        CoredataGroupEntry entry = new CoredataGroupEntry();
                        entry.Uuid = group.ObjectGuid;
                        entry.Name = group.Name;
                        entry.Description = group.Description;

                        filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + group.DistinguishedName);

                        using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                        {
                            directorySearcher.PageSize = 1000;

                            using (var searchResultCollection = directorySearcher.FindAll())
                            {
                                Logger.Information("Found {0} members of group ({1})", searchResultCollection.Count, group.DistinguishedName);
                                foreach (SearchResult searchResult in searchResultCollection)
                                {
                                    string memberGUID = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                    entry.Members.Add(memberGUID);
                                }
                            }
                        }

                        coredataGroup.Groups.Add(entry);
                    }

                    return coredataGroup;
                }
            }

            return null;
        }

        public Dictionary<User, List<JobFunctionsRole>> KombitRolesSync()
        {
            if (string.IsNullOrEmpty(rolesRoot))
            {
                Logger.Warning("Kombit.RoleOU configuration setting is empty");
                return null;
            }

            // read out optional ouFilter
            string ouFilter = Settings.GetStringValue("Kombit.RoleOU.Filter");

            Dictionary<User, List<JobFunctionsRole>> result = new Dictionary<User, List<JobFunctionsRole>>();
            List<JobFunctionsRole> groups = new List<JobFunctionsRole>();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                directoryEntry.Path += "/" + rolesRoot;

                string filter = string.IsNullOrEmpty(ouFilter) ? CreateGroupFilter("!(isDeleted=TRUE)") : CreateGroupFilter("!(isDeleted=TRUE)", ouFilter);

                using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.KombitProperties, SearchScope.Subtree))
                {
                    directorySearcher.PageSize = 1000;

                    using (var searchResultCollection = directorySearcher.FindAll())
                    {
                        Logger.Information("Found {0} groups to find roles from", searchResultCollection.Count);
                        foreach (SearchResult searchResult in searchResultCollection)
                        {
                            string dn = searchResult.Properties.GetValue<string>(propertyResolver.DistinguishedNameProperty, null);
                            string roleName = searchResult.Properties.GetValue<string>(propertyResolver.RoleNameProperty, null);
                            string roleDomain = (string.IsNullOrEmpty(propertyResolver.RoleDomainProperty)) ? null : searchResult.Properties.GetValue<string>(propertyResolver.RoleDomainProperty, null);
                            string cvr = (string.IsNullOrEmpty(propertyResolver.RoleCvrProperty)) ? null : searchResult.Properties.GetValue<string>(propertyResolver.RoleCvrProperty, null);

                            if (roleName == null)
                            {
                                Logger.Debug($"Group {dn} does not have value for property {propertyResolver.RoleNameProperty}");
                                continue;
                            }

                            // if a prefix is configured, and this role starts with this prefix, remove it
                            if (!string.IsNullOrEmpty(roleNamePrefix) && roleName.StartsWith(roleNamePrefix))
                            {
                                roleName = roleName.Substring(roleNamePrefix.Length);

                                if (roleName.Contains("#"))
                                {
                                    string fullName = roleName;

                                    int idx = fullName.IndexOf("#");
                                    roleName = fullName.Substring(0, idx);
                                    fullName = fullName.Substring(idx + 1);
                                    if (fullName.Contains("_"))
                                    {
                                        idx = fullName.IndexOf("_");
                                        cvr = fullName.Substring(0, idx);
                                        roleDomain = fullName.Substring(idx + 1);
                                    }
                                }
                            }

                            // special corner-case for SGs mapping solution
                            if (roleName.Contains("<KOMBIT><JFR>"))
                            {
                                try
                                {
                                    string txt = roleName.Substring("<KOMBIT><JFR>".Length);

                                    // delegated?
                                    if (txt.Contains("<CVR>") && txt.Contains("<ISSUER>"))
                                    {
                                        // expected formats
                                        // <KOMBIT><JFR>roleName<CVR>cvr<ISSUER>roleDomain
                                        // <KOMBIT><JFR>roleName<CVR>cvr<ISSUER>roleDomain</JFR></KOMBIT>

                                        int cvrIdx = txt.IndexOf("<CVR>");
                                        int issuerIdx = txt.IndexOf("<ISSUER>");

                                        // pull out roleName
                                        roleName = txt.Substring(0, cvrIdx);

                                        // pull out cvr
                                        cvr = txt.Substring(cvrIdx + 5, (issuerIdx - cvrIdx - 5));

                                        // pull out roleDomain
                                        roleDomain = txt.Substring(issuerIdx + 8);

                                        if (roleDomain.Contains("</JFR></KOMBIT>"))
                                        {
                                            int idx = roleDomain.IndexOf("</JFR></KOMBIT>");
                                            roleDomain = roleDomain.Substring(0, idx);
                                        }
                                    }
                                    else
                                    {
                                        // expected formats
                                        // <KOMBIT><JFR>roleName</JFR></KOMBIT>
                                        // <KOMBIT><JFR>roleName

                                        roleName = txt;
                                        if (roleName.Contains("</JFR></KOMBIT>"))
                                        {
                                            int idx = roleName.IndexOf("</JFR></KOMBIT>");
                                            roleName = roleName.Substring(0, idx);
                                        }
                                    }
                                }
                                catch (Exception ex)
                                {
                                    Logger.Warning(ex, "Failed to parse content of roleName attribute: " + roleName);
                                }
                            }

                            if (string.IsNullOrEmpty(roleDomain))
                            {
                                roleDomain = defaultDomain;
                            }

                            if (string.IsNullOrEmpty(cvr))
                            {
                                cvr = defaultCvr;
                            }

                            // for some roles, the name or domain actually already contains the full identifier, so respect that.
                            // this is used by a bunch of municipalies in their existing setup, so might as well allow it
                            string roleIdentifier = $"http://{roleDomain}/roles/jobrole/{roleName}/1";
                            if (roleName.StartsWith("http://"))
                            {
                                roleIdentifier = roleName;
                            }
                            else if (roleDomain.StartsWith("http://"))
                            {
                                roleIdentifier = roleName;
                            }

                            // convert AD group to role object
                            groups.Add(new JobFunctionsRole(dn, roleIdentifier, cvr));
                        }
                    }
                }
            }

            // new connection, so we search AD-wide
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                // find all members of those groups
                foreach (JobFunctionsRole group in groups)
                {
                    string filter;

                    if (groupsInGroups)
                    {
                        filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + group.DistinguishedName);
                    }
                    else
                    {
                        filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf=" + group.DistinguishedName);
                    }

                    Logger.Debug("Searching for members using filter: " + filter);

                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} members of group ({1})", searchResultCollection.Count, group.DistinguishedName);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string memberGUID = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                string memberSamAccountName = searchResult.Properties.GetValue<string>(propertyResolver.SAMAccountNameProperty, null);

                                User user = new User { SamAccountName = memberSamAccountName, Uuid = memberGUID };

                                if (!result.ContainsKey(user))
                                {
                                    result.Add(user, new List<JobFunctionsRole>());
                                }

                                result[user].Add(group);
                            }
                        }
                    }
                }
            }

            return result;
        }

        private DirectoryEntry GenerateDirectoryEntry()
        {
            DirectoryEntry directoryEntry = null;

            DomainControllerCollection domains = Domain.GetCurrentDomain().DomainControllers;
            foreach (DomainController controller in domains)
            {
                try
                {
                    directoryEntry = new DirectoryEntry(string.Format("LDAP://{0}", controller.Name));

                    Logger.Verbose("Connected to " + controller.Name);
                    break;
                }
                catch (Exception ex)
                {
                    Logger.Warning("Failed to connect to " + controller.Name + ". Reason:" + ex.Message);
                }
            }

            if (directoryEntry == null)
            {
                throw new Exception("Failed to connect to AD");
            }

            var username = Settings.GetStringValue("ActiveDirectory.Username");
            var password = Settings.GetStringValue("ActiveDirectory.Password");

            if (!string.IsNullOrEmpty(username) && !string.IsNullOrEmpty(password))
            {
                directoryEntry.Username = username;
                directoryEntry.Password = password;
            }

            return directoryEntry;
        }


        [ComImport, Guid("9068270b-0939-11d1-8be1-00c04fd8d503"), InterfaceType(ComInterfaceType.InterfaceIsDual)]
        internal interface IAdsLargeInteger
        {
            long HighPart
            {
                [SuppressUnmanagedCodeSecurity]
                get; [SuppressUnmanagedCodeSecurity]
                set;
            }

            long LowPart
            {
                [SuppressUnmanagedCodeSecurity]
                get; [SuppressUnmanagedCodeSecurity]
                set;
            }
        }

        private CoredataEntry CreateCoredataEntryFromAD(IDictionary properties)
        {
            string Guid = new Guid(properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();

            // Name logic
            string ChosenName = properties.GetValue<String>(propertyResolver.ChosenNameProperty, null);
            string Firstname = properties.GetValue<String>(propertyResolver.FirstnameProperty, null);
            string Surname = properties.GetValue<String>(propertyResolver.SurnameProperty, null);
            string Name = String.IsNullOrEmpty(ChosenName) ? Firstname + " " + Surname : ChosenName;

            // Disabled logic
            var accountControlValue = properties.GetValue<Int32>(propertyResolver.UserAccountControlProperty, 0);

            // AccountExpireProperty
            string accountExpireDate = null;
            try
            {
                if (properties is ResultPropertyCollection)
                {
                    ResultPropertyCollection rpc = (ResultPropertyCollection)properties;

                    long largeInt = (long)rpc[propertyResolver.AccountExpireProperty][0];
                    if (largeInt > 0 && largeInt < long.MaxValue)
                    {
                        var dateTime = DateTime.FromFileTimeUtc(largeInt);

                        accountExpireDate = dateTime.ToString("yyyy-MM-dd");
                    }
                }
                else if (properties is PropertyCollection)
                {
                    PropertyCollection pc = (PropertyCollection)properties;

                    // expire
                    var largeInt = (IAdsLargeInteger)pc[propertyResolver.AccountExpireProperty].Value;
                    var datelong = (largeInt.HighPart << 32) + largeInt.LowPart;

                    if (datelong > 0 && datelong < long.MaxValue)
                    {
                        var dateTime = DateTime.FromFileTimeUtc(datelong);

                        accountExpireDate = dateTime.ToString("yyyy-MM-dd");
                    }
                }
            }
            catch (Exception ex)
            {
                Logger.Warning("Unable to read accountExpire on: " + Name, ex);
            }

            // Create entry
            var user = new CoredataEntry()
            {
                Uuid = Guid,
                Cpr = properties.GetValue<string>(propertyResolver.CprProperty, null),
                Name = Name,
                Email = properties.GetValue<string>(propertyResolver.EmailProperty, null),
                SamAccountName = properties.GetValue<string>(propertyResolver.SAMAccountNameProperty, null),
                Attributes = new Dictionary<string, string>(),
                Deleted = properties.GetValue<bool>(propertyResolver.DeletedProperty, false),
                Disabled = ((accountControlValue & AccountDisable) == AccountDisable),
                NSISAllowed = false,
                ExpireTimestamp = accountExpireDate,
                TransferToNemlogin = false,
                Rid = properties.GetValue<string>(propertyResolver.RidProperty, null)
            };

            // Fill out SubDomain if configured (not really needed for full load, but needed for delta)
            string subDomain = Settings.GetStringValue("Backend.SubDomain");
            if (!string.IsNullOrEmpty(subDomain))
            {
                user.SubDomain = subDomain;
            }

            // Fill out attributes Map with fetched values from AD
            Dictionary<string, string> attributes = Settings.GetStringValues("ActiveDirectory.Attributes.");
            if (attributes != null)
            {
                foreach (var attribute in attributes)
                {
                    string value = properties.GetValue<string>(attribute.Value, null);
                    if (value != null)
                    {
                        user.Attributes.Add(attribute.Key, value);
                    }
                }
            }

            return user;
        }

        private string CreateFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=user");
            allFilters.Add("!(objectclass=computer)");
            allFilters.Add(Settings.GetStringValue("ActiveDirectory.Filter"));

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }

        private string CreateMembeshipFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=user");
            allFilters.Add(Settings.GetStringValue("ActiveDirectory.Filter"));

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }

        private string CreateGroupFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=Group");

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }
    }
}
