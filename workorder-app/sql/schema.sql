-- Work order schema for SQL Server.
-- Keeping it to one table for now Just what's needed to track a work order.

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'wo')
BEGIN
    EXEC('CREATE SCHEMA wo');
END
GO

IF OBJECT_ID('wo.WorkOrders', 'U') IS NOT NULL
    DROP TABLE wo.WorkOrders;
GO

CREATE TABLE wo.WorkOrders
(
    Id              INT IDENTITY(1,1)      NOT NULL,

    Title           NVARCHAR(200)          NOT NULL,
    Description     NVARCHAR(MAX)          NULL,

    -- Only 5 possible values
    Status          VARCHAR(20)            NOT NULL
                        CONSTRAINT DF_WorkOrders_Status DEFAULT ('OPEN'),

    -- Just a name/username
    AssignedTo      NVARCHAR(100)          NULL,

    CreatedAt       DATETIME2(3)           NOT NULL
                        CONSTRAINT DF_WorkOrders_CreatedAt DEFAULT (SYSUTCDATETIME()),
    UpdatedAt       DATETIME2(3)           NOT NULL
                        CONSTRAINT DF_WorkOrders_UpdatedAt DEFAULT (SYSUTCDATETIME()),

    CONSTRAINT PK_WorkOrders PRIMARY KEY CLUSTERED (Id),

    CONSTRAINT CK_WorkOrders_Status CHECK (
        Status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')
    ),

    CONSTRAINT CK_WorkOrders_Title_NotBlank CHECK (LEN(LTRIM(RTRIM(Title))) > 0)
);
GO

-- We filter/sort by status constantly (worklists, dashboards), so it
-- gets its own index.
CREATE NONCLUSTERED INDEX IX_WorkOrders_Status
    ON wo.WorkOrders (Status)
    INCLUDE (Title, AssignedTo, UpdatedAt);
GO

-- make sure UpdatedAt always moves forward on any
-- update, even if the app layer forgets to set it.
CREATE OR ALTER TRIGGER wo.TR_WorkOrders_SetUpdatedAt
ON wo.WorkOrders
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    UPDATE w
        SET UpdatedAt = SYSUTCDATETIME()
    FROM wo.WorkOrders w
    INNER JOIN inserted i ON w.Id = i.Id;
END
GO

-- A few sample rows so there's something to look at right after setup.
INSERT INTO wo.WorkOrders (Title, Description, Status, AssignedTo)
VALUES
    (N'Replace HVAC filter - Building 3', N'Quarterly filter swap, rooftop unit 2', 'OPEN', N'jsmith'),
    (N'Fix leaking valve - Line 4', N'Reported by night shift, drips onto floor', 'IN_PROGRESS', N'mrodriguez'),
    (N'Calibrate scale #12', NULL, 'ON_HOLD', NULL);
GO
