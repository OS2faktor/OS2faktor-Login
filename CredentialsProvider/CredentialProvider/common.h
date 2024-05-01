//
// THIS CODE AND INFORMATION IS PROVIDED "AS IS" WITHOUT WARRANTY OF
// ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
// PARTICULAR PURPOSE.
//
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// This file contains some global variables that describe what our
// sample tile looks like.  For example, it defines what fields a tile has
// and which fields show in which states of LogonUI. This sample illustrates
// the use of each UI field type.

#pragma once
#include "helpers.h"

// The indexes of each of the fields in our credential provider's tiles.
// This is used to reference the different fields by windows login. 
// We will usually just get an index value from winlogin so we use this to keep track of them.
//enum FIELD_ID
//{
//    FI_TILEIMAGE            = 0,
//    FI_LABEL                = 1,
//    FI_LARGE_TEXT           = 2,
//    FI_LOGIN_TARGET         = 3,
//    FI_USERNAME             = 4,
//    FI_PASSWORD             = 5,
//    FI_NEW_PASSWORD         = 6,
//    FI_NEW_PASSWORD_CONFIRM = 7,
//    FI_SUBMIT_BUTTON        = 8,
//    FI_RESET_PASSWORD_LINK  = 9,
//    FI_NUM_FIELDS           = 10   // Note: if new fields are added, keep NUM_FIELDS last.  This is used as a count of the number of fields
//};
enum FIELD_ID
{
    FI_TILEIMAGE = 0,
    FI_LARGE_TEXT = 1,
    FI_RESET_PASSWORD_LINK = 2,
    FI_NUM_FIELDS = 3   // Note: if new fields are added, keep NUM_FIELDS last.  This is used as a count of the number of fields
};

// The first value indicates in which cases the tile should be displayed.
// The following values are allowed:
// CPFS_HIDDEN                          Used to hide a field, this is usefull for hiding tooltips and only showing them as a tooltip
// CPFS_DISPLAY_IN_SELECTED_TILE        SELECTED does NOT refer to if our credentialprovider is selected, it refers to if a USER is selected or not
// CPFS_DISPLAY_IN_DESELECTED_TILE      DESELECTED is the opposite, if no user is selected we need to also ask for their username to log them in
// CPFS_DISPLAY_IN_BOTH                 For fields we need to show in both cases like the change password link
// 
// The second indicates things like whether the field is enabled, whether it has key focus, etc.
// The following values are allowed:
// CPFIS_NONE                           The default, it will be shown but nothing special will happen
// CPFIS_READONLY                       Used to make a normally editable field like username readonly
// CPFIS_DISABLED                       Used to disable a button or link
// CPFIS_FOCUSED                        Used to choose which field has focus, depending on the scenario this could be either username or password
struct FIELD_STATE_PAIR
{
    CREDENTIAL_PROVIDER_FIELD_STATE cpfs;
    CREDENTIAL_PROVIDER_FIELD_INTERACTIVE_STATE cpfis;
};

// These two arrays are seperate because a credential provider might
// want to set up a credential with various combinations of field state pairs
// and field descriptors.
static const FIELD_STATE_PAIR s_rgFieldStatePairs[] =
{    
    { CPFS_DISPLAY_IN_BOTH,              CPFIS_NONE       },    // FI_TILEIMAGE 
    { CPFS_HIDDEN,                       CPFIS_NONE       },    // FI_LARGE_TEXT 
    { CPFS_DISPLAY_IN_BOTH,              CPFIS_NONE       }     // FI_RESET_PASSWORD_LINK 
};

// Field descriptors for unlock and logon.
// The first field is the index of the field.
// The second is the type of the field. 
//      This is used internally in our credentialsProvider to make sure we dont try to read the string value of a bitmap image and so on.
// The third is the name of the field, NOT the value which will appear in the field. 
// The fourth is a GUID that tells windows which type of field this is. 
//      To be a valid V2 CredentialProvider we need to provide a LOGO and a LABEL for showing under the "more sign-in options", 
//      and we should not try to show any image ourselves, windows will show both username and image for us.
static const CREDENTIAL_PROVIDER_FIELD_DESCRIPTOR s_rgCredProvFieldDescriptors[] =
{
    { FI_TILEIMAGE,             CPFT_TILE_IMAGE,      L"Image",                           CPFG_CREDENTIAL_PROVIDER_LOGO   },     // FI_TILEIMAGE 
    { FI_LARGE_TEXT,            CPFT_LARGE_TEXT,      L"OS2faktor Credential Provider",   CPFG_CREDENTIAL_PROVIDER_LABEL  },     // FI_LARGE_TEXT 
    { FI_RESET_PASSWORD_LINK,   CPFT_COMMAND_LINK,    L"Skift Kodeord",                   CPFG_STYLE_LINK_AS_BUTTON       }      // FI_RESET_PASSWORD_LINK 
};
