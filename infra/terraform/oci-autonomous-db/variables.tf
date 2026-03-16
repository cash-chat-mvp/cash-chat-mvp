variable "tenancy_ocid" {
  description = "OCI tenancy OCID."
  type        = string
}

variable "user_ocid" {
  description = "OCI user OCID."
  type        = string
}

variable "fingerprint" {
  description = "API signing key fingerprint."
  type        = string
}

variable "private_key_path" {
  description = "Path to OCI API private key PEM."
  type        = string
}

variable "region" {
  description = "OCI region for the Autonomous Database deployment."
  type        = string
}

variable "compartment_ocid" {
  description = "Compartment OCID where the Autonomous Databases will be created."
  type        = string
}

variable "admin_password" {
  description = "Admin password for both Autonomous Databases."
  type        = string
  sensitive   = true
}

variable "display_name_prefix" {
  description = "Display name prefix shown in OCI Console."
  type        = string
  default     = "autonomous-db"
}

variable "db_name_prefix" {
  description = "Database name prefix. Only letters and digits are kept. Final db_name will end with 01 and 02."
  type        = string
  default     = "AFDB"
}

variable "db_workload" {
  description = "Autonomous Database workload type."
  type        = string
  default     = "OLTP"
}

variable "db_version" {
  description = "Optional database version. Leave null to let OCI pick a supported version for the region."
  type        = string
  default     = null
}

variable "is_access_control_enabled" {
  description = "Enable public endpoint access control list (ACL) for Autonomous Database."
  type        = bool
  default     = true
}

variable "whitelisted_ips" {
  description = "Allowed client CIDRs/IPs when access control is enabled. Replace example CIDR with your egress IP."
  type        = list(string)
  default     = ["203.0.113.4/32"]
}

variable "use_oci_arm_reserved_ip_for_acl" {
  description = "If true, read OCI ARM Terraform state and use instance_reserved_public_ip/32 for ADB ACL."
  type        = bool
  default     = false
}

variable "oci_arm_state_path" {
  description = "Local path to oci-arm terraform.tfstate when use_oci_arm_reserved_ip_for_acl is enabled."
  type        = string
  default     = "../oci-arm/terraform.tfstate"
}

variable "license_model" {
  description = "License model for the Autonomous Database."
  type        = string
  default     = "LICENSE_INCLUDED"
}
