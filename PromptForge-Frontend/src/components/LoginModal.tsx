import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Loader2, Lock, Mail, Sparkles, AlertCircle, ArrowLeft } from "lucide-react";
import { api, setAuthToken, setUserInfo } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";

type ActiveView = "login" | "forgot" | "reset";

export function LoginModal() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  // Flow control
  const [view, setView] = useState<ActiveView>("login");

  // Forgot password flow states
  const [forgotEmail, setForgotEmail] = useState("");
  const [resetCode, setResetCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [isOtpVerified, setIsOtpVerified] = useState(false);

  const navigate = useNavigate();
  const { toast } = useToast();

  const isEmailValid = (val: string) =>
    /^[a-zA-Z0-9_+&*\-]+(?:\.[a-zA-Z0-9_+&*\-]+)*@(?:[a-zA-Z0-9\-]+\.)+[a-zA-Z]{2,}$/.test(
      val.trim().toLowerCase()
    );

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (!email.trim() || !password) {
      setServerError("Please enter both email and password.");
      return;
    }

    setIsLoading(true);
    try {
      const response = await api.login({
        username: email.trim().toLowerCase(),
        password,
      });
      setAuthToken(response.token!);
      setUserInfo({
        id: response.user!.id,
        username: response.user!.username,
        name: response.user!.name,
        role: response.user!.role,
      });
      toast({ title: "Welcome back!", description: "Successfully logged in" });
      navigate("/projects");
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Invalid credentials";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleForgotPasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (!forgotEmail.trim()) {
      setServerError("Please enter your email address.");
      return;
    }

    if (!isEmailValid(forgotEmail)) {
      setServerError("Email is incorrect. Please enter a correct email address.");
      return;
    }

    setIsLoading(true);
    try {
      await api.forgotPassword({
        email: forgotEmail.trim().toLowerCase(),
      });
      toast({ title: "Sent ✓", description: `A 6-digit reset code has been sent to ${forgotEmail.trim().toLowerCase()}.` });
      setIsOtpVerified(false);
      setResetCode("");
      setNewPassword("");
      setView("reset");
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Failed to request password reset";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (resetCode.trim().length !== 6) {
      setServerError("Please enter the complete 6-digit code.");
      return;
    }

    setIsLoading(true);
    try {
      await api.verifyResetCode({
        email: forgotEmail.trim().toLowerCase(),
        code: resetCode.trim(),
      });
      toast({ title: "OTP Verified!", description: "Please set your new password." });
      setIsOtpVerified(true);
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Invalid reset code";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const handleResetPasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);

    if (!resetCode.trim() || !newPassword) {
      setServerError("Please enter the reset code and new password.");
      return;
    }

    if (newPassword.length < 4) {
      setServerError("Password must be at least 4 characters.");
      return;
    }

    setIsLoading(true);
    try {
      await api.resetPassword({
        email: forgotEmail.trim().toLowerCase(),
        code: resetCode.trim(),
        newPassword,
      });
      toast({ title: "Success!", description: "Your password has been reset. Please login." });
      setEmail(forgotEmail);
      setPassword("");
      setIsOtpVerified(false);
      setView("login");
    } catch (error) {
      const msg = error instanceof Error ? error.message : "Failed to reset password";
      setServerError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-background">
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute top-1/3 left-1/3 w-[600px] h-[600px] bg-primary/5 rounded-full blur-3xl" />
      </div>

      <div className="relative w-full max-w-md">
        <div className="bg-card border border-border/50 rounded-2xl p-8 shadow-2xl">

          {/* Back button for secondary views */}
          {view !== "login" && (
            <button
              onClick={() => {
                setView("login");
                setServerError(null);
                setIsOtpVerified(false);
              }}
              className="inline-flex items-center text-sm text-muted-foreground hover:text-foreground mb-4 transition-colors"
            >
              <ArrowLeft className="w-4 h-4 mr-1.5" />
              Back to Login
            </button>
          )}

          {/* VIEW: LOGIN */}
          {view === "login" && (
            <>
              <div className="text-center mb-8">
                <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl bg-primary/20 mb-5">
                  <Sparkles className="w-7 h-7 text-primary" />
                </div>
                <h1 className="text-2xl font-semibold text-foreground mb-2">
                  Welcome to PromptForge
                </h1>
                <p className="text-muted-foreground text-sm">Sign in to continue building</p>
              </div>

              {serverError && (
                <div className="flex items-start gap-3 mb-5 p-3 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive text-sm">
                  <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                  <span>{serverError}</span>
                </div>
              )}

              <form onSubmit={handleLoginSubmit} className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="email" className="text-sm font-medium text-foreground">
                    Email
                  </Label>
                  <div className="relative">
                    <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      id="email"
                      type="email"
                      placeholder="you@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                      disabled={isLoading}
                      autoComplete="email"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex justify-between items-center">
                    <Label htmlFor="password" className="text-sm font-medium text-foreground">
                      Password
                    </Label>
                    <button
                      type="button"
                      onClick={() => {
                        setForgotEmail(email);
                        setView("forgot");
                        setServerError(null);
                      }}
                      className="text-xs text-primary hover:underline font-medium"
                    >
                      Forgot password?
                    </button>
                  </div>
                  <div className="relative">
                    <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      id="password"
                      type="password"
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                      disabled={isLoading}
                      autoComplete="current-password"
                    />
                  </div>
                </div>

                <Button
                  type="submit"
                  disabled={isLoading}
                  className="w-full h-12 bg-primary hover:bg-primary/90 text-primary-foreground font-medium rounded-xl text-sm"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      Signing in...
                    </>
                  ) : (
                    "Sign in"
                  )}
                </Button>
              </form>

              <p className="text-center text-sm text-muted-foreground mt-6">
                Don&apos;t have an account?{" "}
                <Link to="/signup" className="text-primary hover:underline font-medium">
                  Sign up
                </Link>
              </p>
            </>
          )}

          {/* VIEW: FORGOT PASSWORD */}
          {view === "forgot" && (
            <>
              <div className="text-center mb-8">
                <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl bg-primary/20 mb-5">
                  <Lock className="w-7 h-7 text-primary" />
                </div>
                <h1 className="text-2xl font-semibold text-foreground mb-2">
                  Forgot Password?
                </h1>
                <p className="text-muted-foreground text-sm">
                  Enter your registered email to receive a 6-digit reset OTP.
                </p>
              </div>

              {serverError && (
                <div className="flex items-start gap-3 mb-5 p-3 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive text-sm">
                  <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                  <span>{serverError}</span>
                </div>
              )}

              <form onSubmit={handleForgotPasswordSubmit} className="space-y-5">
                <div className="space-y-2">
                  <Label htmlFor="forgot-email" className="text-sm font-medium">
                    Email Address
                  </Label>
                  <div className="relative">
                    <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      id="forgot-email"
                      type="email"
                      placeholder="you@example.com"
                      value={forgotEmail}
                      onChange={(e) => setForgotEmail(e.target.value)}
                      className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                      disabled={isLoading}
                      autoComplete="email"
                    />
                  </div>
                </div>

                <Button
                  type="submit"
                  disabled={isLoading}
                  className="w-full h-12 bg-primary hover:bg-primary/90 font-medium rounded-xl text-sm"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      Sending OTP...
                    </>
                  ) : (
                    "Send Reset OTP"
                  )}
                </Button>
              </form>
            </>
          )}

          {/* VIEW: RESET PASSWORD */}
          {view === "reset" && (
            <>
              <div className="text-center mb-8">
                <div className="inline-flex items-center justify-center w-14 h-14 rounded-xl bg-primary/20 mb-5">
                  <Lock className="w-7 h-7 text-primary" />
                </div>
                <h1 className="text-2xl font-semibold text-foreground mb-2">
                  Reset your Password
                </h1>
                <p className="text-muted-foreground text-sm">
                  {!isOtpVerified
                    ? `Enter the 6-digit code sent to ${forgotEmail}.`
                    : "OTP verified. Set your new password below."}
                </p>
              </div>

              {serverError && (
                <div className="flex items-start gap-3 mb-5 p-3 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive text-sm">
                  <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                  <span>{serverError}</span>
                </div>
              )}

              {/* STEP 1: Verify OTP */}
              {!isOtpVerified ? (
                <form onSubmit={handleVerifyOtpSubmit} className="space-y-5">
                  <div className="space-y-2">
                    <Label htmlFor="reset-code" className="text-sm font-medium">
                      Verification OTP
                    </Label>
                    <Input
                      id="reset-code"
                      type="text"
                      placeholder="Enter 6-digit OTP"
                      maxLength={6}
                      value={resetCode}
                      onChange={(e) =>
                        setResetCode(e.target.value.replace(/\D/g, "").slice(0, 6))
                      }
                      className="h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-center text-xl font-bold tracking-widest"
                      disabled={isLoading}
                      autoFocus
                    />
                  </div>

                  <Button
                    type="submit"
                    disabled={isLoading || resetCode.length !== 6}
                    className="w-full h-12 bg-primary hover:bg-primary/90 font-medium rounded-xl text-sm"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                        Verifying OTP...
                      </>
                    ) : (
                      "Verify OTP Code"
                    )}
                  </Button>
                </form>
              ) : (
                /* STEP 2: Set new password */
                <form onSubmit={handleResetPasswordSubmit} className="space-y-5">
                  <div className="space-y-2">
                    <Label className="text-sm font-medium">Verified OTP</Label>
                    <div className="relative">
                      <Input
                        type="text"
                        value={resetCode}
                        className="h-12 bg-muted/30 border-emerald-500/35 text-emerald-500 rounded-xl text-center text-xl font-bold tracking-widest cursor-not-allowed"
                        disabled
                      />
                      <div className="absolute right-4 top-1/2 -translate-y-1/2 text-emerald-500 font-medium text-xs flex items-center gap-1 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20">
                        ✓ Verified
                      </div>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="new-password" className="text-sm font-medium">
                      New Password
                    </Label>
                    <div className="relative">
                      <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        id="new-password"
                        type="password"
                        placeholder="Min. 4 characters"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        className="pl-10 h-12 bg-muted/50 border-border/50 focus:border-primary rounded-xl text-sm"
                        disabled={isLoading}
                        autoFocus
                      />
                    </div>
                  </div>

                  <Button
                    type="submit"
                    disabled={isLoading}
                    className="w-full h-12 bg-primary hover:bg-primary/90 font-medium rounded-xl text-sm"
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                        Resetting password...
                      </>
                    ) : (
                      "Reset Password"
                    )}
                  </Button>
                </form>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
