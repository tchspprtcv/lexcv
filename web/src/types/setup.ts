export type SetupStatusResponse = {
  initialized: boolean;
};

export type SetupInitializeRequest = {
  clientName: string;
  logo?: string | null;
  adminEmail: string;
  adminPassword: string;
};

export type SetupInitializeResponse = {
  initialized: boolean;
  message: string;
};
