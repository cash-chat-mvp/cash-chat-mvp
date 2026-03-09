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
- The normalized prefix is uppercased and truncated before the `01` / `02` suffix is appended.
- This stack does not configure IP access control during creation.
- Exact service availability depends on region, tenancy limits, and OCI quotas.

Required inputs:
- `tenancy_ocid`
- `user_ocid`
- `fingerprint`
- `private_key_path`
- `region`
- `compartment_ocid`
- `admin_password`

Usage:

```powershell
cd C:\Work\CashChat\cash-chat-mvp\infra\terraform\oci-autonomous-db
copy terraform.tfvars.example terraform.tfvars
notepad terraform.tfvars
terraform init
terraform plan
terraform apply
```

Destroy:

```powershell
terraform destroy
```

References:
- OCI Autonomous Database documentation
  https://docs.oracle.com/iaas/autonomous-database/
- Terraform provider: `oci_database_autonomous_database`
  https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/database_autonomous_database
