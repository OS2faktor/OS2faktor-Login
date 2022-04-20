using OS2faktorADSync.Model;
using Serilog;
using System;
using System.Collections;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.ActiveDirectory;
using System.Linq;

namespace OS2faktorADSync
{
    public class ActiveDirectoryService
    {
        const int AccountDisable = 2;

        public ILogger Logger { get; set; }
        private readonly PropertyResolver propertyResolver = new PropertyResolver();
        private readonly string nsisAllowedGroup;
        private readonly string rolesRoot;
        private readonly string rootMembershipGroup;
        private readonly Boolean loadAllUsers;
        private readonly Boolean groupsInGroups;
        private readonly string defaultCvr;
        private readonly string defaultDomain;

        public ActiveDirectoryService()
        {
            nsisAllowedGroup = Settings.GetStringValue("ActiveDirectory.NSISAllowed.Group");
            rootMembershipGroup = Settings.GetStringValue("ActiveDirectory.Group.Root");
            rolesRoot = Settings.GetStringValue("Kombit.RoleOU");
            defaultDomain = Settings.GetStringValue("Kombit.RoleDomainDefault");
            defaultCvr = Settings.GetStringValue("Kombit.RoleCvrDefault");
            loadAllUsers = Settings.GetBooleanValue("ActiveDirectory.loadAllUsers");
            groupsInGroups = Settings.GetBooleanValue("Kombit.GroupsInGroups");
        }

        public IEnumerable<CoredataEntry> GetFullSyncUsers(out byte[] directorySynchronizationCookie)
        {
            using (var directoryEntry = GenerateDirectoryEntry())
            {
                Dictionary<string, CoredataEntry> result = new Dictionary<string, CoredataEntry>();
                string filter;

                if (loadAllUsers)
                {
                    // Full load
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*");
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

                    // Additional search for members of a specific group (Recursive / transative)
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.PageSize = 1000;

                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                string Guid = new Guid(searchResult.Properties.GetValue<System.Byte[]>(propertyResolver.ObjectGuidProperty, null)).ToString();
                                if (result.ContainsKey(Guid))
                                {
                                    result[Guid].NSISAllowed = true;
                                }
                            }
                        }
                    }
                }
                else
                {
                    // Only load nsis
                    filter = CreateFilter("!(isDeleted=TRUE)", propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                    using (var directorySearcher = new DirectorySearcher(directoryEntry, filter, propertyResolver.AllProperties, SearchScope.Subtree))
                    {
                        directorySearcher.DirectorySynchronization = new DirectorySynchronization(DirectorySynchronizationOptions.None);
                        using (var searchResultCollection = directorySearcher.FindAll())
                        {
                            Logger.Information("Found {0} users in Active Directory", searchResultCollection.Count);

                            foreach (SearchResult searchResult in searchResultCollection)
                            {
                                Logger.Verbose("Full sync searchResult: {@searchResult}", searchResult);

                                CoredataEntry user = CreateCoredataEntryFromAD(searchResult.Properties);
                                user.NSISAllowed = true;

                                if (user.IsActive() && user.IsValid())
                                {
                                    result.Add(user.Uuid, user);
                                }
                            }
                        }

                        directorySynchronizationCookie = directorySearcher.DirectorySynchronization.GetDirectorySynchronizationCookie();
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
                                entry.NSISAllowed = true;
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

                        if (loadAllUsers)
                        {
                            filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + group.DistinguishedName);
                        }
                        else
                        {
                            filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf:1.2.840.113556.1.4.1941:=" + group.DistinguishedName, "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                        }

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
            if (rolesRoot == null)
            {
                Logger.Warning("Kombit.RoleOU configuration setting is empty");
                return null;
            }

            Dictionary<User, List<JobFunctionsRole>> result = new Dictionary<User, List<JobFunctionsRole>>();
            List<JobFunctionsRole> groups = new List<JobFunctionsRole>();

            using (var directoryEntry = GenerateDirectoryEntry())
            {
                directoryEntry.Path += "/" + rolesRoot;

                string filter = CreateGroupFilter("!(isDeleted=TRUE)");

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
                            string roleDomain = searchResult.Properties.GetValue<string>(propertyResolver.RoleDomainProperty, defaultDomain);
                            string cvr = searchResult.Properties.GetValue<string>(propertyResolver.RoleCvrProperty, defaultCvr);

                            if (roleName == null)
                            {
                                Logger.Error($"Group {dn} does not have value for property {propertyResolver.RoleNameProperty}");
                                continue;
                            }

                            string roleIdentifier = $"http://{roleDomain}/roles/jobrole/{roleName}/1";

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

                    if (loadAllUsers)
                    {
                        if (groupsInGroups)
                        {
                            filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf=:1.2.840.113556.1.4.1941:" + group.DistinguishedName);
                        }
                        else
                        {
                            filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf=" + group.DistinguishedName);
                        }
                    }
                    else
                    {
                        if (groupsInGroups)
                        {
                            filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf=:1.2.840.113556.1.4.1941:" + group.DistinguishedName, "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                        }
                        else
                        {
                            filter = CreateMembeshipFilter(propertyResolver.CprProperty + "=*", "memberOf=" + group.DistinguishedName, "memberOf:1.2.840.113556.1.4.1941:=" + nsisAllowedGroup);
                        }
                    }

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
                NSISAllowed = false
            };

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
