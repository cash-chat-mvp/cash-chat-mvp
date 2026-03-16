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
  instance_metadata = merge(
    {
      ssh_authorized_keys = trimspace(var.ssh_public_key)
    },
    var.cloud_init == "" ? {} : {
      user_data = base64encode(var.cloud_init)
    }
  )
}

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

data "oci_core_images" "selected_arm_image" {
  compartment_id           = var.compartment_ocid
  operating_system         = var.image_operating_system
  operating_system_version = var.image_operating_system_version
  shape                    = var.shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_virtual_network" "this" {
  compartment_id = var.compartment_ocid
  cidr_block     = var.vcn_cidr
  display_name   = "${var.name_prefix}-vcn"
  dns_label      = var.vcn_dns_label
}

resource "oci_core_internet_gateway" "this" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_virtual_network.this.id
  display_name   = "${var.name_prefix}-igw"
  enabled        = true
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_virtual_network.this.id
  display_name   = "${var.name_prefix}-public-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.this.id
  }
}

resource "oci_core_security_list" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_virtual_network.this.id
  display_name   = "${var.name_prefix}-public-sl"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  ingress_security_rules {
    protocol = "6"
    source   = var.ssh_allowed_cidr

    tcp_options {
      min = 22
      max = 22
    }
  }

  dynamic "ingress_security_rules" {
    for_each = var.open_tcp_ports
    content {
      protocol = "6"
      source   = var.ingress_allowed_cidr

      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }
}

resource "oci_core_subnet" "public" {
  compartment_id             = var.compartment_ocid
  vcn_id                     = oci_core_virtual_network.this.id
  cidr_block                 = var.subnet_cidr
  display_name               = "${var.name_prefix}-public-subnet"
  dns_label                  = var.subnet_dns_label
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.public.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_instance" "arm_instance" {
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[var.availability_domain_index].name
  compartment_id      = var.compartment_ocid
  display_name        = var.instance_name
  shape               = var.shape

  shape_config {
    ocpus         = var.ocpus
    memory_in_gbs = var.memory_in_gbs
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.public.id
    assign_public_ip = true
    display_name     = "${var.instance_name}-vnic"
    hostname_label   = var.hostname_label
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.selected_arm_image.images[0].id
    boot_volume_size_in_gbs = var.boot_volume_size_in_gbs
  }

  instance_options {
    are_legacy_imds_endpoints_disabled = true
  }

  metadata = local.instance_metadata

  preserve_boot_volume = false
}

data "oci_core_vnic_attachments" "arm_instance_vnics" {
  compartment_id = var.compartment_ocid
  instance_id    = oci_core_instance.arm_instance.id

  depends_on = [oci_core_instance.arm_instance]
}

data "oci_core_vnic" "arm_instance_primary_vnic" {
  vnic_id = data.oci_core_vnic_attachments.arm_instance_vnics.vnic_attachments[0].vnic_id
}

data "oci_core_private_ips" "arm_instance_primary_private_ips" {
  vnic_id = data.oci_core_vnic.arm_instance_primary_vnic.id
}

resource "oci_core_public_ip" "arm_reserved_public_ip" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.instance_name}-reserved-public-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.arm_instance_primary_private_ips.private_ips[0].id
}
