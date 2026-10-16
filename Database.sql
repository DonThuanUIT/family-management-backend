CREATE TYPE "MemberStatus" AS ENUM (
  'Living',
  'Deceased'
);

CREATE TYPE "AccountStatus" AS ENUM (
  'PendingApproval',
  'Active',
  'Suspended',
  'Deactivated'
);

CREATE TYPE "InviteType" AS ENUM (
  'GeneralClanInvite',
  'PositionLinkCode'
);

CREATE TYPE "JoinProposalType" AS ENUM (
  'ExistingBranchLeaf',
  'NewChildBranch'
);

CREATE TYPE "ApprovalStatus" AS ENUM (
  'Pending',
  'Approved',
  'Rejected'
);

CREATE TYPE "EventStatus" AS ENUM (
  'Draft',
  'RegistrationOpen',
  'RegistrationClosed',
  'Ongoing',
  'Ended',
  'Closed'
);

CREATE TYPE "EventRole" AS ENUM (
  'HeadOfOrganizer',
  'OrganizerMember',
  'EventTreasurer'
);

CREATE TYPE "TaskStatus" AS ENUM (
  'Unassigned',
  'InProgress',
  'Completed',
  'Overdue'
);

CREATE TYPE "AlertLevel" AS ENUM (
  'Reminder24H',
  'OverdueAlert12H'
);

CREATE TYPE "ContributionType" AS ENUM (
  'Cash',
  'InKind'
);

CREATE TYPE "PaymentMethod" AS ENUM (
  'Cash',
  'BankTransfer'
);

CREATE TYPE "InKindProcessingStatus" AS ENUM (
  'Pending',
  'Consumed',
  'Returned',
  'AddedToCommonAssets'
);

CREATE TYPE "SettlementStatus" AS ENUM (
  'PendingReview',
  'Valid',
  'NeedsExplanation',
  'Settled'
);

CREATE TYPE "LedgerTransactionType" AS ENUM (
  'SurplusTransfer',
  'AdjustmentTransaction'
);

CREATE TABLE "FAMILY_BRANCH" (
  "branch_id" varchar(20) PRIMARY KEY,
  "clan_name" nvarchar(150),
  "branch_name" nvarchar(100) NOT NULL,
  "generation_level" int NOT NULL,
  "parent_branch_id" varchar(20),
  "representative_id" varchar(20),
  "description" text,
  "created_at" datetime DEFAULT (now())
);

CREATE TABLE "MEMBER" (
  "member_id" varchar(20) PRIMARY KEY,
  "full_name" nvarchar(100) NOT NULL,
  "date_of_birth" date,
  "gender" nvarchar(10) NOT NULL,
  "generation" int NOT NULL,
  "status" "MemberStatus" NOT NULL DEFAULT 'Living',
  "death_date_lunar" varchar(50),
  "branch_id" varchar(20) NOT NULL,
  "is_placeholder" boolean DEFAULT false,
  "created_at" datetime DEFAULT (now())
);

CREATE TABLE "USER_ACCOUNT" (
  "account_id" varchar(20) PRIMARY KEY,
  "member_id" varchar(20) UNIQUE,
  "phone_number" varchar(15) UNIQUE NOT NULL,
  "email" varchar(100) UNIQUE,
  "password_hash" varchar(255) NOT NULL,
  "account_status" "AccountStatus" NOT NULL DEFAULT 'Active',
  "created_at" datetime DEFAULT (now())
);

CREATE TABLE "SYSTEM_ROLE" (
  "role_id" varchar(20) PRIMARY KEY,
  "role_name" nvarchar(50) NOT NULL,
  "description" nvarchar(255)
);

CREATE TABLE "ACCOUNT_ROLE" (
  "account_id" varchar(20) NOT NULL,
  "role_id" varchar(20) NOT NULL,
  "assigned_at" datetime DEFAULT (now()),
  PRIMARY KEY ("account_id", "role_id")
);

CREATE TABLE "INVITATION_CODE" (
  "invite_code" varchar(30) PRIMARY KEY,
  "invite_type" "InviteType" NOT NULL,
  "shareable_url" varchar(255),
  "target_branch_id" varchar(20),
  "target_member_id" varchar(20) UNIQUE,
  "max_uses" int DEFAULT 1,
  "used_count" int DEFAULT 0,
  "expires_at" datetime,
  "is_active" boolean DEFAULT true,
  "created_by_account_id" varchar(20) NOT NULL,
  "created_at" datetime DEFAULT (now())
);

CREATE TABLE "BRANCH_JOIN_REQUEST" (
  "request_id" varchar(20) PRIMARY KEY,
  "account_id" varchar(20) UNIQUE NOT NULL,
  "invite_code" varchar(30) NOT NULL,
  "full_name" nvarchar(100) NOT NULL,
  "date_of_birth" date,
  "gender" nvarchar(10) NOT NULL,
  "proposal_type" "JoinProposalType" NOT NULL,
  "target_branch_id" varchar(20) NOT NULL,
  "proposed_new_branch_name" nvarchar(100),
  "proposed_generation" int NOT NULL,
  "relationship_note" nvarchar(255),
  "approval_status" "ApprovalStatus" NOT NULL DEFAULT 'Pending',
  "reviewed_by_account_id" varchar(20),
  "rejection_reason" text,
  "created_at" datetime DEFAULT (now()),
  "reviewed_at" datetime
);

CREATE TABLE "EVENT" (
  "event_id" varchar(20) PRIMARY KEY,
  "event_name" nvarchar(100) NOT NULL,
  "event_datetime" datetime NOT NULL,
  "event_date_lunar" varchar(50),
  "location" nvarchar(255) NOT NULL,
  "estimated_budget" decimal(18,2) DEFAULT 0,
  "description" text,
  "status" "EventStatus" NOT NULL DEFAULT 'Draft',
  "created_by" varchar(20) NOT NULL,
  "closed_at" datetime
);

CREATE TABLE "EVENT_PERSONNEL" (
  "event_id" varchar(20) NOT NULL,
  "member_id" varchar(20) NOT NULL,
  "event_role" "EventRole" NOT NULL,
  "is_read_only" boolean DEFAULT false,
  "assigned_date" datetime DEFAULT (now()),
  PRIMARY KEY ("event_id", "member_id", "event_role")
);

CREATE TABLE "FAMILY_REGISTRATION" (
  "registration_id" varchar(20) PRIMARY KEY,
  "event_id" varchar(20) NOT NULL,
  "representative_member_id" varchar(20) NOT NULL,
  "total_attendees" int NOT NULL,
  "vegetarian_meals" int DEFAULT 0,
  "savory_meals" int DEFAULT 0,
  "dietary_requirements" nvarchar(255),
  "transportation_notes" nvarchar(255),
  "registration_date" datetime DEFAULT (now())
);

CREATE TABLE "REGISTRATION_MEMBER_DETAIL" (
  "registration_id" varchar(20) NOT NULL,
  "member_id" varchar(20) NOT NULL,
  "is_checked_in" boolean DEFAULT false,
  PRIMARY KEY ("registration_id", "member_id")
);

CREATE TABLE "EVENT_NOTIFICATION" (
  "notification_id" varchar(20) PRIMARY KEY,
  "event_id" varchar(20) NOT NULL,
  "title" nvarchar(200) NOT NULL,
  "content_changes" text NOT NULL,
  "sent_time" datetime DEFAULT (now()),
  "triggered_by" varchar(20) NOT NULL
);

CREATE TABLE "EVENT_TASK" (
  "task_id" varchar(20) PRIMARY KEY,
  "event_id" varchar(20) NOT NULL,
  "task_name" nvarchar(100) NOT NULL,
  "details" text,
  "advance_budget" decimal(18,2) DEFAULT 0,
  "deadline" datetime NOT NULL,
  "status" "TaskStatus" NOT NULL DEFAULT 'Unassigned',
  "assigned_branch_id" varchar(20),
  "assigned_member_id" varchar(20),
  "created_by" varchar(20) NOT NULL
);

CREATE TABLE "TASK_PROGRESS_ALERT" (
  "alert_id" varchar(20) PRIMARY KEY,
  "task_id" varchar(20) NOT NULL,
  "alert_level" "AlertLevel" NOT NULL,
  "recipient_account_id" varchar(20) NOT NULL,
  "alert_content" text NOT NULL,
  "sent_time" datetime DEFAULT (now()),
  "is_read" boolean DEFAULT false
);

CREATE TABLE "CONTRIBUTION" (
  "contribution_id" varchar(20) PRIMARY KEY,
  "event_id" varchar(20) NOT NULL,
  "payer_member_id" varchar(20) NOT NULL,
  "receiver_account_id" varchar(20) NOT NULL,
  "contribution_date" datetime DEFAULT (now()),
  "contribution_type" "ContributionType" NOT NULL,
  "total_monetary_value" decimal(18,2) NOT NULL
);

CREATE TABLE "CONTRIBUTION_MEMBER_DETAIL" (
  "contribution_id" varchar(20) NOT NULL,
  "member_id" varchar(20) NOT NULL,
  "recorded_amount" decimal(18,2) NOT NULL,
  PRIMARY KEY ("contribution_id", "member_id")
);

CREATE TABLE "CASH_CONTRIBUTION" (
  "contribution_id" varchar(20) PRIMARY KEY,
  "amount" decimal(18,2) NOT NULL,
  "payment_method" "PaymentMethod" NOT NULL,
  "receipt_number" varchar(30) UNIQUE NOT NULL
);

CREATE TABLE "IN_KIND_CONTRIBUTION" (
  "contribution_id" varchar(20) PRIMARY KEY,
  "item_name" nvarchar(100) NOT NULL,
  "quantity" decimal(10,2) NOT NULL,
  "unit" nvarchar(20) NOT NULL,
  "estimated_value" decimal(18,2) DEFAULT 0,
  "conversion_method" nvarchar(255) NOT NULL,
  "converted_monetary_value" decimal(18,2) NOT NULL,
  "processing_status" "InKindProcessingStatus" NOT NULL DEFAULT 'Pending'
);

CREATE TABLE "CLAN_ASSET" (
  "asset_id" varchar(20) PRIMARY KEY,
  "asset_name" nvarchar(100) NOT NULL,
  "quantity" int NOT NULL,
  "unit" nvarchar(20) NOT NULL,
  "assessed_value" decimal(18,2) DEFAULT 0,
  "storage_location" nvarchar(255),
  "origin_contribution_id" varchar(20)
);

CREATE TABLE "ADVANCE_VOUCHER" (
  "advance_voucher_id" varchar(20) PRIMARY KEY,
  "task_id" varchar(20) NOT NULL,
  "recipient_member_id" varchar(20) NOT NULL,
  "advance_amount" decimal(18,2) NOT NULL,
  "received_date" datetime NOT NULL,
  "settlement_deadline" datetime NOT NULL,
  "commitment_notes" text,
  "created_by_account_id" varchar(20) NOT NULL
);

CREATE TABLE "EXPENSE_SETTLEMENT_VOUCHER" (
  "settlement_voucher_id" varchar(20) PRIMARY KEY,
  "advance_voucher_id" varchar(20) UNIQUE NOT NULL,
  "actual_expense_amount" decimal(18,2) NOT NULL,
  "difference_amount" decimal(18,2) NOT NULL,
  "invoice_attachment_url" varchar(500),
  "expense_notes" text,
  "treasurer_account_id" varchar(20) NOT NULL,
  "status" "SettlementStatus" NOT NULL,
  "rejection_reason" text,
  "settled_at" datetime DEFAULT (now())
);

CREATE TABLE "LEDGER_ADJUSTMENT_REQUEST" (
  "request_id" varchar(20) PRIMARY KEY,
  "original_transaction_id" varchar(20) NOT NULL,
  "proposed_adjustment_amount" decimal(18,2) NOT NULL,
  "adjustment_reason" text NOT NULL,
  "evidence_attachment_url" varchar(500),
  "proposed_by_account_id" varchar(20) NOT NULL,
  "approval_status" "ApprovalStatus" NOT NULL DEFAULT 'Pending',
  "reviewed_by_account_id" varchar(20),
  "rejection_reason" text,
  "created_at" datetime DEFAULT (now()),
  "reviewed_at" datetime
);

CREATE TABLE "IMMUTABLE_LEDGER" (
  "transaction_id" varchar(20) PRIMARY KEY,
  "event_id" varchar(20),
  "adjustment_request_id" varchar(20) UNIQUE,
  "original_transaction_id" varchar(20),
  "transaction_type" "LedgerTransactionType" NOT NULL,
  "amount" decimal(18,2) NOT NULL,
  "running_balance" decimal(18,2) NOT NULL,
  "description" text NOT NULL,
  "created_by_account_id" varchar(20) NOT NULL,
  "approved_by_account_id" varchar(20) NOT NULL,
  "record_hash" varchar(64) UNIQUE NOT NULL,
  "created_at" datetime DEFAULT (now())
);

COMMENT ON COLUMN "FAMILY_BRANCH"."branch_id" IS 'Unique identifier for family branch';

COMMENT ON COLUMN "FAMILY_BRANCH"."clan_name" IS 'Populated on Root Branch (US-00, US-02)';

COMMENT ON COLUMN "FAMILY_BRANCH"."branch_name" IS 'Official branch name';

COMMENT ON COLUMN "FAMILY_BRANCH"."generation_level" IS '1 for Root Branch, parent + 1 for child';

COMMENT ON COLUMN "FAMILY_BRANCH"."parent_branch_id" IS 'Recursive FK pointing to parent branch (NULL for Root)';

COMMENT ON COLUMN "FAMILY_BRANCH"."representative_id" IS 'FK to MEMBER acting as Branch Head (Nullable)';

COMMENT ON COLUMN "FAMILY_BRANCH"."description" IS 'Branch history, ancestral hall info, notes (US-02)';

COMMENT ON COLUMN "MEMBER"."member_id" IS 'Unique member ID (US-03, US-18)';

COMMENT ON COLUMN "MEMBER"."status" IS 'Living / Deceased';

COMMENT ON COLUMN "MEMBER"."death_date_lunar" IS 'Lunar death date for ancestor worship if Deceased';

COMMENT ON COLUMN "MEMBER"."branch_id" IS 'FK to FAMILY_BRANCH';

COMMENT ON COLUMN "MEMBER"."is_placeholder" IS 'True if pre-created as virtual profile in US-18';

COMMENT ON COLUMN "USER_ACCOUNT"."account_id" IS 'System account ID';

COMMENT ON COLUMN "USER_ACCOUNT"."member_id" IS 'Nullable while waiting for US-17 approval; 1-1 once linked';

COMMENT ON COLUMN "USER_ACCOUNT"."account_status" IS 'PendingApproval for US-17';

COMMENT ON COLUMN "SYSTEM_ROLE"."role_id" IS 'ROLE_CLAN_HEAD, ROLE_BRANCH_HEAD, ROLE_MEMBER, ROLE_ADMIN';

COMMENT ON COLUMN "INVITATION_CODE"."invite_code" IS 'Unique Invite Code or Position Link Code (US-16, US-18)';

COMMENT ON COLUMN "INVITATION_CODE"."invite_type" IS 'GeneralClanInvite (US-16) / PositionLinkCode (US-18)';

COMMENT ON COLUMN "INVITATION_CODE"."shareable_url" IS 'Full shareable registration link';

COMMENT ON COLUMN "INVITATION_CODE"."target_branch_id" IS 'Optional default branch for general invite';

COMMENT ON COLUMN "INVITATION_CODE"."target_member_id" IS 'Populated ONLY for PositionLinkCode (US-18, US-19)';

COMMENT ON COLUMN "INVITATION_CODE"."max_uses" IS '1 for PositionLinkCode; multiple/unlimited for GeneralClanInvite';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."request_id" IS 'Self-proposed position registration via General Invite (US-17)';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."account_id" IS 'New user account submitting the proposal';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."invite_code" IS 'General invite code used';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."proposal_type" IS 'ExistingBranchLeaf / NewChildBranch';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."target_branch_id" IS 'Branch to join OR Parent branch if creating new child branch';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."proposed_new_branch_name" IS 'Filled if proposal_type = NewChildBranch';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."relationship_note" IS 'Explanation of lineage for reviewer';

COMMENT ON COLUMN "BRANCH_JOIN_REQUEST"."reviewed_by_account_id" IS 'Clan Head or Branch Head approving/rejecting';

COMMENT ON COLUMN "EVENT"."event_id" IS 'Unique event ID (US-05)';

COMMENT ON COLUMN "EVENT"."event_datetime" IS 'Solar date & time';

COMMENT ON COLUMN "EVENT"."event_date_lunar" IS 'Lunar date string for traditional tracking';

COMMENT ON COLUMN "EVENT"."closed_at" IS 'Timestamp when event is financially locked (US-13)';

COMMENT ON COLUMN "EVENT_PERSONNEL"."event_id" IS 'Contextual RBAC per event';

COMMENT ON COLUMN "EVENT_PERSONNEL"."member_id" IS 'Assigned member ID';

COMMENT ON COLUMN "EVENT_PERSONNEL"."event_role" IS 'HeadOfOrganizer / OrganizerMember / EventTreasurer';

COMMENT ON COLUMN "EVENT_PERSONNEL"."is_read_only" IS 'Auto set to TRUE when EVENT is Closed (US-13)';

COMMENT ON COLUMN "FAMILY_REGISTRATION"."registration_id" IS 'Household registration for event (US-06)';

COMMENT ON COLUMN "FAMILY_REGISTRATION"."representative_member_id" IS 'Household head registering';

COMMENT ON COLUMN "FAMILY_REGISTRATION"."total_attendees" IS 'Total registered attendees';

COMMENT ON COLUMN "REGISTRATION_MEMBER_DETAIL"."member_id" IS 'Specific individual attendee identity';

COMMENT ON COLUMN "REGISTRATION_MEMBER_DETAIL"."is_checked_in" IS 'Actual attendance check-in';

COMMENT ON COLUMN "EVENT_TASK"."task_id" IS 'Unique task item ID (US-07)';

COMMENT ON COLUMN "EVENT_TASK"."assigned_branch_id" IS 'Nullable - Assigned to entire branch';

COMMENT ON COLUMN "EVENT_TASK"."assigned_member_id" IS 'Nullable - Assigned to specific individual';

COMMENT ON COLUMN "TASK_PROGRESS_ALERT"."alert_id" IS '24h reminder & 12h escalation alert (US-08)';

COMMENT ON COLUMN "TASK_PROGRESS_ALERT"."alert_level" IS 'Reminder24H / OverdueAlert12H';

COMMENT ON COLUMN "TASK_PROGRESS_ALERT"."recipient_account_id" IS 'Assignee (24H) or Branch Head (12H)';

COMMENT ON COLUMN "CONTRIBUTION"."contribution_id" IS 'Base contribution entity (US-09, US-10)';

COMMENT ON COLUMN "CONTRIBUTION"."payer_member_id" IS 'Representative handing cash/item to treasurer';

COMMENT ON COLUMN "CONTRIBUTION"."receiver_account_id" IS 'Event treasurer receiving transaction';

COMMENT ON COLUMN "CONTRIBUTION"."contribution_type" IS 'Cash / InKind';

COMMENT ON COLUMN "CONTRIBUTION"."total_monetary_value" IS 'Cash amount OR converted monetary value of in-kind item';

COMMENT ON COLUMN "CONTRIBUTION_MEMBER_DETAIL"."member_id" IS 'Individual member receiving financial allocation';

COMMENT ON COLUMN "CONTRIBUTION_MEMBER_DETAIL"."recorded_amount" IS 'Allocated monetary value used by TinhTongDongGop() (US-11)';

COMMENT ON COLUMN "CASH_CONTRIBUTION"."contribution_id" IS 'Child entity - Cash (PK is FK, US-09)';

COMMENT ON COLUMN "CASH_CONTRIBUTION"."payment_method" IS 'Cash / BankTransfer';

COMMENT ON COLUMN "CASH_CONTRIBUTION"."receipt_number" IS 'Auto-generated official receipt code';

COMMENT ON COLUMN "IN_KIND_CONTRIBUTION"."contribution_id" IS 'Child entity - In-Kind (PK is FK, US-10)';

COMMENT ON COLUMN "IN_KIND_CONTRIBUTION"."conversion_method" IS 'Organizer rate / Invoice value / Mutual agreement';

COMMENT ON COLUMN "IN_KIND_CONTRIBUTION"."converted_monetary_value" IS 'Equivalent monetary value in VND';

COMMENT ON COLUMN "CLAN_ASSET"."asset_id" IS 'Accumulated clan asset after event (US-10)';

COMMENT ON COLUMN "CLAN_ASSET"."storage_location" IS 'Ancestral hall location';

COMMENT ON COLUMN "CLAN_ASSET"."origin_contribution_id" IS 'Traces back to IN_KIND_CONTRIBUTION';

COMMENT ON COLUMN "ADVANCE_VOUCHER"."advance_voucher_id" IS 'Advance payment voucher (US-12)';

COMMENT ON COLUMN "EXPENSE_SETTLEMENT_VOUCHER"."settlement_voucher_id" IS 'Expense reconciliation & settlement voucher (US-12)';

COMMENT ON COLUMN "EXPENSE_SETTLEMENT_VOUCHER"."difference_amount" IS 'actual - advance (>0: Pay extra; <0: Refund)';

COMMENT ON COLUMN "EXPENSE_SETTLEMENT_VOUCHER"."invoice_attachment_url" IS 'Uploaded receipt/invoice images for verification';

COMMENT ON COLUMN "EXPENSE_SETTLEMENT_VOUCHER"."expense_notes" IS 'Detailed breakdown of purchased items';

COMMENT ON COLUMN "EXPENSE_SETTLEMENT_VOUCHER"."status" IS 'Valid / NeedsExplanation (Blocks event closing in US-13)';

COMMENT ON COLUMN "EXPENSE_SETTLEMENT_VOUCHER"."rejection_reason" IS 'Populated when status = NeedsExplanation';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."request_id" IS 'Offset proposal workflow before inserting into Ledger (US-15)';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."original_transaction_id" IS 'Target historical transaction in IMMUTABLE_LEDGER';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."proposed_adjustment_amount" IS 'Positive or Negative offset value';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."adjustment_reason" IS 'Detailed explanation of error';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."proposed_by_account_id" IS 'Treasurer proposing adjustment';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."approval_status" IS 'Pending / Approved / Rejected';

COMMENT ON COLUMN "LEDGER_ADJUSTMENT_REQUEST"."reviewed_by_account_id" IS 'Clan Head reviewing proposal';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."transaction_id" IS 'Immutable common ledger - STRICTLY INSERT-ONLY (US-13, US-15)';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."event_id" IS 'Populated if transaction_type = SurplusTransfer';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."adjustment_request_id" IS 'Populated if transaction_type = AdjustmentTransaction';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."original_transaction_id" IS 'Recursive FK pointing to root record being adjusted';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."amount" IS 'Positive: Fund increase; Negative: Fund decrease';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."running_balance" IS 'Cumulative available fund balance after transaction';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."created_by_account_id" IS 'Treasurer executing entry';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."approved_by_account_id" IS 'Clan Head who approved entry';

COMMENT ON COLUMN "IMMUTABLE_LEDGER"."record_hash" IS 'SHA-256 hash chain for tamper-proof audit trail';

ALTER TABLE "FAMILY_BRANCH" ADD FOREIGN KEY ("parent_branch_id") REFERENCES "FAMILY_BRANCH" ("branch_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "FAMILY_BRANCH" ADD FOREIGN KEY ("representative_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "MEMBER" ADD FOREIGN KEY ("branch_id") REFERENCES "FAMILY_BRANCH" ("branch_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "MEMBER" ADD FOREIGN KEY ("member_id") REFERENCES "USER_ACCOUNT" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ACCOUNT_ROLE" ADD FOREIGN KEY ("account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ACCOUNT_ROLE" ADD FOREIGN KEY ("role_id") REFERENCES "SYSTEM_ROLE" ("role_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "INVITATION_CODE" ADD FOREIGN KEY ("target_branch_id") REFERENCES "FAMILY_BRANCH" ("branch_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "MEMBER" ADD FOREIGN KEY ("member_id") REFERENCES "INVITATION_CODE" ("target_member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "INVITATION_CODE" ADD FOREIGN KEY ("created_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "USER_ACCOUNT" ADD FOREIGN KEY ("account_id") REFERENCES "BRANCH_JOIN_REQUEST" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BRANCH_JOIN_REQUEST" ADD FOREIGN KEY ("invite_code") REFERENCES "INVITATION_CODE" ("invite_code") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BRANCH_JOIN_REQUEST" ADD FOREIGN KEY ("target_branch_id") REFERENCES "FAMILY_BRANCH" ("branch_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "BRANCH_JOIN_REQUEST" ADD FOREIGN KEY ("reviewed_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT" ADD FOREIGN KEY ("created_by") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_PERSONNEL" ADD FOREIGN KEY ("event_id") REFERENCES "EVENT" ("event_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_PERSONNEL" ADD FOREIGN KEY ("member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "FAMILY_REGISTRATION" ADD FOREIGN KEY ("event_id") REFERENCES "EVENT" ("event_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "FAMILY_REGISTRATION" ADD FOREIGN KEY ("representative_member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "REGISTRATION_MEMBER_DETAIL" ADD FOREIGN KEY ("registration_id") REFERENCES "FAMILY_REGISTRATION" ("registration_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "REGISTRATION_MEMBER_DETAIL" ADD FOREIGN KEY ("member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_NOTIFICATION" ADD FOREIGN KEY ("event_id") REFERENCES "EVENT" ("event_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_NOTIFICATION" ADD FOREIGN KEY ("triggered_by") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_TASK" ADD FOREIGN KEY ("event_id") REFERENCES "EVENT" ("event_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_TASK" ADD FOREIGN KEY ("assigned_branch_id") REFERENCES "FAMILY_BRANCH" ("branch_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_TASK" ADD FOREIGN KEY ("assigned_member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EVENT_TASK" ADD FOREIGN KEY ("created_by") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TASK_PROGRESS_ALERT" ADD FOREIGN KEY ("task_id") REFERENCES "EVENT_TASK" ("task_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "TASK_PROGRESS_ALERT" ADD FOREIGN KEY ("recipient_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION" ADD FOREIGN KEY ("event_id") REFERENCES "EVENT" ("event_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION" ADD FOREIGN KEY ("payer_member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION" ADD FOREIGN KEY ("receiver_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION_MEMBER_DETAIL" ADD FOREIGN KEY ("contribution_id") REFERENCES "CONTRIBUTION" ("contribution_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION_MEMBER_DETAIL" ADD FOREIGN KEY ("member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION" ADD FOREIGN KEY ("contribution_id") REFERENCES "CASH_CONTRIBUTION" ("contribution_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CONTRIBUTION" ADD FOREIGN KEY ("contribution_id") REFERENCES "IN_KIND_CONTRIBUTION" ("contribution_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "CLAN_ASSET" ADD FOREIGN KEY ("origin_contribution_id") REFERENCES "IN_KIND_CONTRIBUTION" ("contribution_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ADVANCE_VOUCHER" ADD FOREIGN KEY ("task_id") REFERENCES "EVENT_TASK" ("task_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ADVANCE_VOUCHER" ADD FOREIGN KEY ("recipient_member_id") REFERENCES "MEMBER" ("member_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ADVANCE_VOUCHER" ADD FOREIGN KEY ("created_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "ADVANCE_VOUCHER" ADD FOREIGN KEY ("advance_voucher_id") REFERENCES "EXPENSE_SETTLEMENT_VOUCHER" ("advance_voucher_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "EXPENSE_SETTLEMENT_VOUCHER" ADD FOREIGN KEY ("treasurer_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "LEDGER_ADJUSTMENT_REQUEST" ADD FOREIGN KEY ("original_transaction_id") REFERENCES "IMMUTABLE_LEDGER" ("transaction_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "LEDGER_ADJUSTMENT_REQUEST" ADD FOREIGN KEY ("proposed_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "LEDGER_ADJUSTMENT_REQUEST" ADD FOREIGN KEY ("reviewed_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "IMMUTABLE_LEDGER" ADD FOREIGN KEY ("event_id") REFERENCES "EVENT" ("event_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "LEDGER_ADJUSTMENT_REQUEST" ADD FOREIGN KEY ("request_id") REFERENCES "IMMUTABLE_LEDGER" ("adjustment_request_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "IMMUTABLE_LEDGER" ADD FOREIGN KEY ("original_transaction_id") REFERENCES "IMMUTABLE_LEDGER" ("transaction_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "IMMUTABLE_LEDGER" ADD FOREIGN KEY ("created_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE "IMMUTABLE_LEDGER" ADD FOREIGN KEY ("approved_by_account_id") REFERENCES "USER_ACCOUNT" ("account_id") DEFERRABLE INITIALLY IMMEDIATE;
