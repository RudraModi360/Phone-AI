package com.example.tools.builtin

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.provider.ContactsContract
import com.example.service.Parameters
import com.example.service.PropertySchema
import com.example.tools.AndroidPathResolver
import com.example.tools.BaseTool
import com.example.tools.PermissionManager
import com.example.tools.PermissionType
import com.example.tools.RiskLevel
import com.example.tools.ToolResult

/**
 * Android Contacts Tool - Full CRUD operations using real ContentResolver APIs.
 * NO MOCK DATA - All operations use actual phone contacts via ContactsContract.
 */
class ContactsTool : BaseTool {
    override val name = "contacts"
    override val description = "Full CRUD operations on Android contacts: list, search, get details, create, update, and delete contacts."
    override val riskLevel = RiskLevel.SAFE  // Read is safe, write operations handled with WRITE_CONTACTS permission

    override val parameters = Parameters(
        type = "OBJECT",
        properties = mapOf(
            "operation" to PropertySchema(
                type = "STRING",
                description = "Operation: 'list', 'search', 'get_details', 'create', 'update', 'delete'"
            ),
            "query" to PropertySchema(
                type = "STRING",
                description = "Search term (name or phone number) for search operation"
            ),
            "limit" to PropertySchema(
                type = "INTEGER",
                description = "Maximum number of contacts to return (default: 30)"
            ),
            "contact_id" to PropertySchema(
                type = "STRING",
                description = "Contact ID for get_details, update, or delete operations"
            ),
            "name" to PropertySchema(
                type = "STRING",
                description = "Contact display name for create/update operations"
            ),
            "phone" to PropertySchema(
                type = "STRING",
                description = "Phone number for create/update operations"
            ),
            "email" to PropertySchema(
                type = "STRING",
                description = "Email address for create/update operations"
            )
        ),
        required = emptyList()
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val operation = args["operation"] as? String ?: "list"
        val query = args["query"] as? String
        val limit = (args["limit"] as? Number)?.toInt() ?: 30
        val contactId = args["contact_id"] as? String
        val name = args["name"] as? String
        val phone = args["phone"] as? String
        val email = args["email"] as? String

        val context = AndroidPathResolver.getContext()
            ?: return ToolResult.error("Application context not available. Ensure AndroidPathResolver is initialized.")

        // Determine required permission type based on operation
        val permissionType = when (operation) {
            "create", "update", "delete" -> PermissionType.CONTACTS_WRITE
            else -> PermissionType.CONTACTS_READ
        }

        // Check and request permissions using the enhanced PermissionManager
        val permissionError = PermissionManager.ensurePermissions(permissionType)
        if (permissionError != null) {
            return ToolResult.error(permissionError)
        }

        val resolver = context.contentResolver

        return try {
            when (operation) {
                "list" -> listContacts(resolver, query, limit)
                "search" -> {
                    if (query.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'query' for search operation")
                    }
                    searchContacts(resolver, query, limit)
                }
                "get_details" -> {
                    if (contactId.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'contact_id' for get_details operation")
                    }
                    getContactDetails(resolver, contactId)
                }
                "create" -> {
                    if (name.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'name' for create operation")
                    }
                    createContact(resolver, name, phone, email)
                }
                "update" -> {
                    if (contactId.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'contact_id' for update operation")
                    }
                    updateContact(resolver, contactId, name, phone, email)
                }
                "delete" -> {
                    if (contactId.isNullOrBlank()) {
                        return ToolResult.error("Missing required parameter: 'contact_id' for delete operation")
                    }
                    deleteContact(resolver, contactId)
                }
                else -> ToolResult.error("Unknown operation: '$operation'. Use: list, search, get_details, create, update, delete")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${PermissionManager.getPermissionDeniedMessage(context, permissionType)}")
        } catch (e: Exception) {
            ToolResult.error("Contacts operation failed: ${e.message}")
        }
    }

    /**
     * List contacts from the device. Uses real ContactsContract queries.
     */
    private fun listContacts(resolver: ContentResolver, query: String?, limit: Int): ToolResult {
        val contactsList = mutableListOf<Map<String, String>>()
        
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ContactsContract.Contacts.STARRED
        )

        val selection = if (!query.isNullOrBlank()) {
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        } else {
            null
        }
        val selectionArgs = if (!query.isNullOrBlank()) {
            arrayOf("%$query%")
        } else {
            null
        }

        val cursor: Cursor? = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use { c ->
            val idIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val hasPhoneIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            val starredIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

            var count = 0
            while (c.moveToNext() && count < limit) {
                val id = c.getString(idIndex) ?: continue
                val displayName = c.getString(nameIndex) ?: "Unknown"
                val hasPhone = c.getInt(hasPhoneIndex) > 0
                val starred = c.getInt(starredIndex) > 0

                val contactMap = mutableMapOf(
                    "id" to id,
                    "name" to displayName,
                    "has_phone" to hasPhone.toString(),
                    "starred" to starred.toString()
                )

                // Get primary phone if available
                if (hasPhone) {
                    getPrimaryPhone(resolver, id)?.let { phone ->
                        contactMap["phone"] = phone
                    }
                }

                // Get primary email
                getPrimaryEmail(resolver, id)?.let { email ->
                    contactMap["email"] = email
                }

                contactsList.add(contactMap)
                count++
            }
        }

        if (contactsList.isEmpty()) {
            return ToolResult.success(
                "No contacts found on this device.",
                mapOf("count" to 0, "contacts" to emptyList<Map<String, String>>())
            )
        }

        val formattedResult = contactsList.joinToString("\n") { c ->
            val star = if (c["starred"] == "true") "★ " else ""
            "$star${c["name"]} (ID: ${c["id"]})\n  Phone: ${c["phone"] ?: "N/A"} | Email: ${c["email"] ?: "N/A"}"
        }

        return ToolResult.success(
            "Found ${contactsList.size} contacts:\n\n$formattedResult",
            mapOf("count" to contactsList.size, "contacts" to contactsList)
        )
    }

    /**
     * Search contacts by name or phone number.
     */
    private fun searchContacts(resolver: ContentResolver, query: String, limit: Int): ToolResult {
        // Search by display name
        val nameResults = mutableListOf<Map<String, String>>()
        
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        // Search by name
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$query%"),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val hasPhoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext() && nameResults.size < limit) {
                val id = cursor.getString(idIndex) ?: continue
                val name = cursor.getString(nameIndex) ?: "Unknown"
                val hasPhone = cursor.getInt(hasPhoneIndex) > 0

                val contactMap = mutableMapOf(
                    "id" to id,
                    "name" to name,
                    "match_type" to "name"
                )

                if (hasPhone) {
                    getPrimaryPhone(resolver, id)?.let { phone ->
                        contactMap["phone"] = phone
                    }
                }

                getPrimaryEmail(resolver, id)?.let { email ->
                    contactMap["email"] = email
                }

                nameResults.add(contactMap)
            }
        }

        // Also search by phone number
        val phoneResults = mutableListOf<Map<String, String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("%$query%"),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext() && phoneResults.size < limit) {
                val id = cursor.getString(idIndex) ?: continue
                // Skip if already in name results
                if (nameResults.any { it["id"] == id }) continue

                val name = cursor.getString(nameIndex) ?: "Unknown"
                val phone = cursor.getString(phoneIndex) ?: ""

                phoneResults.add(mapOf(
                    "id" to id,
                    "name" to name,
                    "phone" to phone,
                    "match_type" to "phone"
                ))
            }
        }

        val allResults = (nameResults + phoneResults).take(limit)

        if (allResults.isEmpty()) {
            return ToolResult.success(
                "No contacts found matching '$query'.",
                mapOf("count" to 0, "query" to query, "contacts" to emptyList<Map<String, String>>())
            )
        }

        val formattedResult = allResults.joinToString("\n") { c ->
            "${c["name"]} (ID: ${c["id"]}) [matched by ${c["match_type"]}]\n  Phone: ${c["phone"] ?: "N/A"} | Email: ${c["email"] ?: "N/A"}"
        }

        return ToolResult.success(
            "Found ${allResults.size} contacts matching '$query':\n\n$formattedResult",
            mapOf("count" to allResults.size, "query" to query, "contacts" to allResults)
        )
    }

    /**
     * Get detailed information for a specific contact.
     */
    private fun getContactDetails(resolver: ContentResolver, contactId: String): ToolResult {
        val details = mutableMapOf<String, Any>()
        details["id"] = contactId

        // Get basic contact info
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.LOOKUP_KEY
            ),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                details["name"] = cursor.getString(0) ?: "Unknown"
                details["starred"] = cursor.getInt(1) > 0
                details["photo_uri"] = cursor.getString(2) ?: ""
                details["lookup_key"] = cursor.getString(3) ?: ""
            } else {
                return ToolResult.error("Contact with ID '$contactId' not found.")
            }
        } ?: return ToolResult.error("Failed to query contact details.")

        // Get all phone numbers
        val phones = mutableListOf<Map<String, String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex) ?: continue
                val type = cursor.getInt(typeIndex)
                val label = cursor.getString(labelIndex)
                val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    AndroidPathResolver.getContext()?.resources,
                    type,
                    label
                ).toString()

                phones.add(mapOf("number" to number, "type" to typeLabel))
            }
        }
        details["phones"] = phones

        // Get all email addresses
        val emails = mutableListOf<Map<String, String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.LABEL
            ),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE)
            val labelIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.LABEL)

            while (cursor.moveToNext()) {
                val address = cursor.getString(addressIndex) ?: continue
                val type = cursor.getInt(typeIndex)
                val label = cursor.getString(labelIndex)
                val typeLabel = ContactsContract.CommonDataKinds.Email.getTypeLabel(
                    AndroidPathResolver.getContext()?.resources,
                    type,
                    label
                ).toString()

                emails.add(mapOf("address" to address, "type" to typeLabel))
            }
        }
        details["emails"] = emails

        // Get organization info
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                details["company"] = cursor.getString(0) ?: ""
                details["job_title"] = cursor.getString(1) ?: ""
            }
        }

        // Format output
        val phonesStr = if (phones.isNotEmpty()) {
            phones.joinToString("\n    ") { "${it["type"]}: ${it["number"]}" }
        } else "None"

        val emailsStr = if (emails.isNotEmpty()) {
            emails.joinToString("\n    ") { "${it["type"]}: ${it["address"]}" }
        } else "None"

        val formatted = """
Contact Details for ID: $contactId
================================
Name: ${details["name"]}
Starred: ${if (details["starred"] == true) "Yes ★" else "No"}
Company: ${details["company"] ?: "N/A"}
Job Title: ${details["job_title"] ?: "N/A"}

Phone Numbers:
    $phonesStr

Email Addresses:
    $emailsStr
        """.trimIndent()

        return ToolResult.success(formatted, details)
    }

    /**
     * Create a new contact with the provided information.
     * Uses batch operations for atomic insert.
     */
    private fun createContact(resolver: ContentResolver, name: String, phone: String?, email: String?): ToolResult {
        val operations = ArrayList<ContentProviderOperation>()

        // Insert raw contact
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // Insert display name
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        // Insert phone number if provided
        if (!phone.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
        }

        // Insert email if provided
        if (!email.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    .build()
            )
        }

        return try {
            val results = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val rawContactUri = results[0].uri
            val rawContactId = rawContactUri?.lastPathSegment

            // Get the actual contact ID
            var contactId: String? = null
            if (rawContactId != null) {
                resolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawContactId),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contactId = cursor.getString(0)
                    }
                }
            }

            ToolResult.success(
                "Contact created successfully!\nName: $name\nPhone: ${phone ?: "N/A"}\nEmail: ${email ?: "N/A"}\nContact ID: ${contactId ?: rawContactId}",
                mapOf(
                    "success" to true,
                    "contact_id" to (contactId ?: rawContactId ?: "unknown"),
                    "raw_contact_id" to (rawContactId ?: "unknown"),
                    "name" to name,
                    "phone" to (phone ?: ""),
                    "email" to (email ?: "")
                )
            )
        } catch (e: Exception) {
            ToolResult.error("Failed to create contact: ${e.message}")
        }
    }

    /**
     * Update an existing contact's information.
     */
    private fun updateContact(resolver: ContentResolver, contactId: String, name: String?, phone: String?, email: String?): ToolResult {
        if (name.isNullOrBlank() && phone.isNullOrBlank() && email.isNullOrBlank()) {
            return ToolResult.error("At least one of 'name', 'phone', or 'email' must be provided for update")
        }

        // First, verify the contact exists and get raw contact ID
        var rawContactId: String? = null
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                rawContactId = cursor.getString(0)
            }
        }

        if (rawContactId == null) {
            return ToolResult.error("Contact with ID '$contactId' not found.")
        }

        val operations = ArrayList<ContentProviderOperation>()

        // Update name if provided
        if (!name.isNullOrBlank()) {
            operations.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(rawContactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
        }

        // Update or add phone if provided
        if (!phone.isNullOrBlank()) {
            // Try to update existing phone first
            val phoneExists = resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                null
            )?.use { it.count > 0 } ?: false

            if (phoneExists) {
                operations.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(rawContactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .build()
                )
            } else {
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId!!.toLong())
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }
        }

        // Update or add email if provided
        if (!email.isNullOrBlank()) {
            val emailExists = resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
                null
            )?.use { it.count > 0 } ?: false

            if (emailExists) {
                operations.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(rawContactId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .build()
                )
            } else {
                operations.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId!!.toLong())
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                        .build()
                )
            }
        }

        return try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operations)
            
            val updates = mutableListOf<String>()
            if (!name.isNullOrBlank()) updates.add("Name: $name")
            if (!phone.isNullOrBlank()) updates.add("Phone: $phone")
            if (!email.isNullOrBlank()) updates.add("Email: $email")

            ToolResult.success(
                "Contact updated successfully!\nContact ID: $contactId\nUpdated fields:\n  ${updates.joinToString("\n  ")}",
                mapOf(
                    "success" to true,
                    "contact_id" to contactId,
                    "updated_name" to (name ?: ""),
                    "updated_phone" to (phone ?: ""),
                    "updated_email" to (email ?: "")
                )
            )
        } catch (e: Exception) {
            ToolResult.error("Failed to update contact: ${e.message}")
        }
    }

    /**
     * Delete a contact by its ID.
     */
    private fun deleteContact(resolver: ContentResolver, contactId: String): ToolResult {
        // First get contact name for confirmation message
        var contactName = "Unknown"
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(0) ?: "Unknown"
            }
        }

        // Delete the contact using the lookup URI for more reliable deletion
        var lookupKey: String? = null
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                lookupKey = cursor.getString(0)
            }
        }

        if (lookupKey == null) {
            return ToolResult.error("Contact with ID '$contactId' not found.")
        }

        return try {
            val deleteUri = ContactsContract.Contacts.getLookupUri(contactId.toLong(), lookupKey)
            val rowsDeleted = resolver.delete(deleteUri, null, null)

            if (rowsDeleted > 0) {
                ToolResult.success(
                    "Contact deleted successfully!\nDeleted: $contactName (ID: $contactId)",
                    mapOf(
                        "success" to true,
                        "contact_id" to contactId,
                        "deleted_name" to contactName,
                        "rows_deleted" to rowsDeleted
                    )
                )
            } else {
                ToolResult.error("Failed to delete contact. No rows affected.")
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to delete contact: ${e.message}")
        }
    }

    /**
     * Get the primary phone number for a contact.
     */
    private fun getPrimaryPhone(resolver: ContentResolver, contactId: String): String? {
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    /**
     * Get the primary email address for a contact.
     */
    private fun getPrimaryEmail(resolver: ContentResolver, contactId: String): String? {
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            "${ContactsContract.CommonDataKinds.Email.IS_PRIMARY} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }
}
