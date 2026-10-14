INSERT INTO contract_template_versions (id, template_id, version_number, content_template, status, created_by, created_at, updated_at) 
VALUES (
  1, 
  1, 
  1, 
  '<div style="font-family: Times New Roman, Times, serif; color: #111; line-height: 1.6;"><div style="text-align: center; margin-bottom: 24px;"><h3 style="margin: 0; text-transform: uppercase; font-size: 15px; font-weight: bold;">CONG HOA XA HOI CHU NGHIA VIET NAM</h3><p style="margin: 2px 0 0; font-size: 14px; text-decoration: underline; font-weight: bold;">Doc lap - Tu do - Hanh phuc</p></div><h2 style="text-align: center; font-size: 18px; font-weight: bold; margin-bottom: 20px; text-transform: uppercase;">THOA THUAN HOP DONG THUC TAP</h2><p>Hom nay, ngay {{currentDay}} thang {{currentMonth}} nam {{currentYear}}, chung toi gom co:</p><h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">BEN A: BEN TIEP NHAN THUC TAP (CONG TY)</h4><p>- Dai dien: Phong Quan Tri Nhan Su</p><p>- Bo phan tiep nhan: {{department}}</p><p>- Nguoi huong dan: {{supervisorName}}</p><h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">BEN B: THUC TAP SINH</h4><p>- Ho va ten: {{internFullName}}</p><p>- Email: {{internEmail}}</p><p>- So dien thoai: {{internPhone}}</p><p>- Truong: {{university}} ({{major}})</p><h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">DIEU 1: VI TRI VA THOI GIAN</h4><p>- Vi tri: {{position}}</p><p>- Thoi han: Tu ngay {{startDate}} den ngay {{endDate}}</p><h4 style="margin: 12px 0 6px; font-weight: bold; font-size: 14px;">DIEU 2: PHU CAP</h4><p>- Phu cap: {{formattedAllowance}} VND / thang</p></div>', 
  'ACTIVE', 
  'SYSTEM_INITIALIZER', 
  NOW(), 
  NOW()
);
