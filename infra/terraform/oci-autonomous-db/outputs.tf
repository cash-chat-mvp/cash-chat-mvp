output "autonomous_database_ids" {
  description = "OCIDs of the created Autonomous Databases."
  value       = oci_database_autonomous_database.adb[*].id
}

output "autonomous_database_display_names" {
  description = "Display names of the created Autonomous Databases."
  value       = oci_database_autonomous_database.adb[*].display_name
}

output "autonomous_database_db_names" {
  description = "Database names of the created Autonomous Databases."
  value       = oci_database_autonomous_database.adb[*].db_name
}

output "autonomous_database_states" {
  description = "Lifecycle states of the created Autonomous Databases."
  value       = oci_database_autonomous_database.adb[*].state
}

output "effective_acl_whitelisted_ips" {
  description = "Final ADB ACL CIDRs applied after optional oci-arm reserved IP resolution."
  value       = local.effective_whitelisted_ips
}
