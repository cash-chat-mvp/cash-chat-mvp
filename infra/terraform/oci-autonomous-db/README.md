# OCI Autonomous Database Terraform

This stack creates two Autonomous Databases in OCI.

Resources created:
- Autonomous Database `01`
- Autonomous Database `02`

Current behavior:
- Creates two databases with `count = 2`
- Default workload: `OLTP`
- Default display names:
  `autonomous-db-01`, `autonomous-db-02`
- Default database names:
  `AFDB01`, `AFDB02`

Notes:
- `db_name_prefix` is normalized to letters and digits only.
- Leading digits in `db_name_prefix` are removed so generated DB names always start with a letter.
- The normalized prefix is uppercased and truncated before the `01` / `02` suffix is appended.
- Public endpoint ACL is enabled by default (`is_access_control_enabled = true`).
- Replace `whitelisted_ips` in `terraform.tfvars` with your actual client egress/fixed CIDR.
- You can set `use_oci_arm_reserved_ip_for_acl = true` to auto-read `instance_reserved_public_ip` from the `oci-arm` stack state.
- Exact service availability depends on region, tenancy limits, and OCI quotas.

Required inputs:
- `tenancy_ocid`
- `user_ocid`
- `fingerprint`
- `private_key_path`
- `region`
- `compartment_ocid`
- `admin_password`
- `whitelisted_ips` (recommended to set explicitly for your environment)

Optional cross-stack inputs:
- `use_oci_arm_reserved_ip_for_acl`
- `oci_arm_state_path`

Usage:

```powershell
cd C:\Work\CashChat\cash-chat-mvp\infra\terraform\oci-autonomous-db
copy terraform.tfvars.example terraform.tfvars
notepad terraform.tfvars
terraform init
terraform plan
terraform apply
```

If using `use_oci_arm_reserved_ip_for_acl = true`, apply `oci-arm` first so its `terraform.tfstate` already contains `instance_reserved_public_ip`.

Destroy:

```powershell
terraform destroy
```

References:
- OCI Autonomous Database documentation
  https://docs.oracle.com/iaas/autonomous-database/
- Terraform provider: `oci_database_autonomous_database`
  https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/database_autonomous_database
