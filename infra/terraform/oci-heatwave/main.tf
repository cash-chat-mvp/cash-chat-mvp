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

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

resource "oci_core_network_security_group" "mysql" {
  compartment_id = var.compartment_ocid
  vcn_id         = var.existing_vcn_id
  display_name   = "${var.name_prefix}-mysql-nsg"
}

resource "oci_core_network_security_group_security_rule" "mysql_ingress" {
  network_security_group_id = oci_core_network_security_group.mysql.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.mysql_ingress_cidr
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = 3306
      max = 3306
    }
  }
}

resource "oci_core_network_security_group_security_rule" "mysqlx_ingress" {
  network_security_group_id = oci_core_network_security_group.mysql.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.mysql_ingress_cidr
  source_type               = "CIDR_BLOCK"

  tcp_options {
    destination_port_range {
      min = 33060
      max = 33060
    }
  }
}

resource "oci_core_subnet" "private" {
  compartment_id             = var.compartment_ocid
  vcn_id                     = var.existing_vcn_id
  cidr_block                 = var.subnet_cidr
  display_name               = "${var.name_prefix}-private-subnet"
  dns_label                  = var.subnet_dns_label
  prohibit_public_ip_on_vnic = true
}

resource "oci_mysql_mysql_db_system" "this" {
  compartment_id      = var.compartment_ocid
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[var.availability_domain_index].name
  subnet_id           = oci_core_subnet.private.id
  shape_name          = var.db_system_shape_name

  display_name            = var.db_system_name
  description             = var.db_system_description
  admin_username          = var.admin_username
  admin_password          = var.admin_password
  hostname_label          = var.hostname_label
  data_storage_size_in_gb = 50
  is_highly_available     = false
  nsg_ids                 = [oci_core_network_security_group.mysql.id]

  deletion_policy {
    automatic_backup_retention = "DELETE"
    final_backup               = "SKIP_FINAL_BACKUP"
    is_delete_protected        = false
  }
}

resource "oci_mysql_heat_wave_cluster" "this" {
  db_system_id          = oci_mysql_mysql_db_system.this.id
  cluster_size          = 1
  shape_name            = var.heatwave_shape_name
  is_lakehouse_enabled  = var.enable_lakehouse
}
