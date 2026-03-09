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
  db_name_prefix    = length(local.normalized_prefix) > 0 ? substr(local.normalized_prefix, 0, min(length(local.normalized_prefix), 12)) : "AFDB"
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
  license_model                       = var.license_model
  is_auto_scaling_enabled             = false
  is_auto_scaling_for_storage_enabled = false
  source                              = "NONE"

}
