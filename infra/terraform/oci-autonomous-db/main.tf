terraform {
  required_version = ">= 1.5.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 6.0.0"
    }
  }
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

locals {
  normalized_prefix = upper(replace(var.db_name_prefix, "/[^0-9A-Za-z]/", ""))
  alpha_prefix      = replace(local.normalized_prefix, "/^[0-9]+/", "")
  db_name_prefix    = length(local.alpha_prefix) > 0 ? substr(local.alpha_prefix, 0, min(length(local.alpha_prefix), 12)) : "AFDB"
}

data "terraform_remote_state" "oci_arm" {
  count   = var.use_oci_arm_reserved_ip_for_acl ? 1 : 0
  backend = "local"
  config = {
    path = var.oci_arm_state_path
  }
}

locals {
  oci_arm_reserved_public_ip = var.use_oci_arm_reserved_ip_for_acl ? try(data.terraform_remote_state.oci_arm[0].outputs.instance_reserved_public_ip, null) : null
  effective_whitelisted_ips  = var.is_access_control_enabled ? (local.oci_arm_reserved_public_ip != null && local.oci_arm_reserved_public_ip != "" ? [format("%s/32", local.oci_arm_reserved_public_ip)] : var.whitelisted_ips) : null
}

resource "oci_database_autonomous_database" "adb" {
  count = 2

  compartment_id = var.compartment_ocid
  display_name   = format("%s-%02d", var.display_name_prefix, count.index + 1)
  db_name        = format("%s%02d", local.db_name_prefix, count.index + 1)

  admin_password                      = var.admin_password
  db_workload                         = var.db_workload
  db_version                          = var.db_version
  is_free_tier                        = true
  is_access_control_enabled           = var.is_access_control_enabled ? true : null
  license_model                       = var.license_model
  is_auto_scaling_enabled             = false
  is_auto_scaling_for_storage_enabled = false
  source                              = "NONE"
  whitelisted_ips                     = local.effective_whitelisted_ips

  lifecycle {
    precondition {
      condition     = !var.is_access_control_enabled || !var.use_oci_arm_reserved_ip_for_acl || (local.oci_arm_reserved_public_ip != null && local.oci_arm_reserved_public_ip != "")
      error_message = "Access control is enabled and use_oci_arm_reserved_ip_for_acl=true, but instance_reserved_public_ip was not found in the oci-arm terraform state. Apply oci-arm first or set oci_arm_state_path correctly."
    }
  }
}
